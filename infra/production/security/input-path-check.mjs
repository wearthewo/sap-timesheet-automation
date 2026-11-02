// Run only against already-built, locally available images. No pull, service startup or volumes.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { isAbsolute, relative, resolve, sep } from 'node:path';

const [proxy, keycloak, output] = process.argv.slice(2);
for (const id of [proxy, keycloak]) assert.match(id ?? '', /^sha256:[a-f0-9]{64}$/, 'Use exact local configuration image IDs');
assert.ok(output, 'Provide an ignored evidence output directory');
const root = resolve(import.meta.dirname, '../../..');
const out = resolve(output);
const withinEvidence = relative(resolve(root, '.verification'), out);
assert.ok(withinEvidence && !isAbsolute(withinEvidence) && withinEvidence !== '..' && !withinEvidence.startsWith('..' + sep),
  'Write evidence beneath repository .verification only');
mkdirSync(out, { recursive: true });
const hash = text => createHash('sha256').update(text).digest('hex');
const evidence = { observedAt: new Date().toISOString(), approval: 'NONE', proxy, keycloak, checks: [] };
const common = ['run', '--rm', '--pull=never', '--platform=linux/amd64', '--network=none', '--read-only',
  '--cap-drop=ALL', '--security-opt=no-new-privileges', '--memory=384m', '--cpus=1', '--pids-limit=64',
  '--tmpfs', '/tmp:rw,nosuid,nodev,size=64m'];
function docker(label, args) {
  let result;
  try {
    result = execFileSync('docker', args, { encoding: 'utf8', timeout: 60000, maxBuffer: 8 * 1024 * 1024 });
  } catch (error) {
    writeFileSync(resolve(out, `${label}-failure.txt`), String(error.stdout ?? '') + String(error.stderr ?? ''));
    throw new Error(`${label} failed (status ${error.status}); inspect ${out}/${label}-failure.txt`);
  }
  writeFileSync(resolve(out, `${label}.txt`), result);
  evidence.checks.push({ label, args, sha256: hash(result) });
  return result;
}
for (const id of [proxy, keycloak]) {
  const [image] = JSON.parse(docker(id === proxy ? 'proxy-inspect' : 'keycloak-inspect', ['image', 'inspect', id]));
  assert.equal(image.Id, id);
  assert.equal(image.Architecture, 'amd64');
}
const config = JSON.parse(docker('proxy-adapt', [...common,
  '--mount', `type=bind,src=${resolve(root, 'infra/production/Caddyfile')},dst=/check/Caddyfile,readonly`,
  '-e', 'APP_HOST=app.example.test', '-e', 'AUTH_HOST=auth.example.test', '-e', 'TLS_CONFIG=operator@example.test',
  '--entrypoint', 'caddy', proxy, 'adapt', '--config', '/check/Caddyfile', '--adapter', 'caddyfile']));
assert.equal(config.admin.disabled, true);
assert.equal(config.apps.pki, undefined);
const handlers = new Set();
function walk(value) {
  if (Array.isArray(value)) { value.forEach(walk); return; }
  if (!value || typeof value !== 'object') return;
  if (value.handler) handlers.add(value.handler);
  if (value.issuers) for (const issuer of value.issuers) assert.equal(issuer.module, 'acme');
  assert.equal(value.on_demand, undefined, 'Unexpected on-demand issuance');
  Object.values(value).forEach(walk);
}
walk(config);
assert.ok(handlers.has('reverse_proxy'));
assert.ok(!handlers.has('acme_server'));
const allowed = new Set(['headers', 'log_append', 'request_body', 'reverse_proxy', 'static_response', 'subroute', 'workforce_guard']);
for (const handler of handlers) assert.ok(allowed.has(handler), `Review new handler ${handler}`);
const log = docker('keycloak-probe', [...common,
  '--mount', `type=bind,src=${resolve(root, 'infra/production/security')},dst=/check,readonly`,
  '--entrypoint', 'java', keycloak, '-Xmx192m', '-Djava.awt.headless=true', '-Xlog:class+load=info',
  '--class-path', '/opt/keycloak/lib/lib/main/*:/opt/keycloak/lib/lib/boot/*', '/check/InputPathProbe.java']);
assert.equal((log.match(/^PASS /gm) ?? []).length, 5);
// Restrict the claim to the QR path actually executed, not every graphics path in Keycloak.
assert.doesNotMatch(log, /\[class,load\] (?:sun\.font\.|java\.awt\.Font(?:\s|\$))/);
evidence.configHashes = Object.fromEntries(['Caddyfile', 'compose.yaml', 'workforce-realm.json', 'keycloak.Dockerfile']
  .map(file => [file, hash(readFileSync(resolve(root, 'infra/production', file)))]));
const realm = JSON.parse(readFileSync(resolve(root, 'infra/production/workforce-realm.json'), 'utf8'));
assert.equal((realm.identityProviders ?? []).length, 0);
assert.equal(realm.clients.every(client => client.protocol === 'openid-connect'), true);
writeFileSync(resolve(out, 'runtime-evidence.json'), JSON.stringify(evidence, null, 2) + '\n');
console.log('PASS exact AMD64 images; proxy configuration boundaries; five benign Keycloak probes; no font classes loaded by QR probe');
console.log('No vulnerability approval: wider transitive input paths still require review.');
