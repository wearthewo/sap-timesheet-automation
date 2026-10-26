// Shell-free bootstrap for the distroless runtime. Never print secret contents.
import {readFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';

export function loadSecrets(env = process.env, read = readFileSync) {
  const names = (env.SECRET_NAMES ?? '').split(/\s+/).filter(Boolean);
  for (const required of ['NEXTAUTH_SECRET', 'KEYCLOAK_CLIENT_SECRET']) {
    if (!names.includes(required)) throw Error(`Required secret not configured: ${required}`);
  }
  for (const name of names) {
    if (!/^[A-Z_]+$/.test(name)) throw Error('Invalid secret name');
    let value;
    try { value = read(`/run/secrets/${name}`, 'utf8').replace(/\n+$/, ''); }
    catch { throw Error(`Required secret file missing: ${name}`); }
    if (value.length < 16) throw Error(`Secret too short: ${name}`);
    if (/REPLACE|CHANGEME/.test(value)) throw Error(`Placeholder secret rejected: ${name}`);
    if (value.includes('\0')) throw Error(`Invalid secret: ${name}`);
    env[name] = value;
  }
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  try { process.umask(0o077); loadSecrets(); }
  catch (error) { console.error(error.message); process.exit(1); }
  // Keep Node as the single server process; preserve normal signal handling.
  await import('./server.js');
}
