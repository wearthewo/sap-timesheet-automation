import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {parseEnv,validate} from './validate-env.mjs';
const source=name=>readFileSync(new URL(name,import.meta.url),'utf8');
const sample=parseEnv(source('./production.env.example'));
const valid={...sample,APP_HOST:'app.example.org',AUTH_HOST:'auth.example.org',SMTP_HOST:'smtp.example.org',SMTP_USERNAME:'sender',MAIL_FROM:'sender@example.org',TLS_CONFIG:'operator@example.org',KEYCLOAK_ADMIN_USERNAME:'private-operator'};
test('production preflight rejects placeholders and absent settings',()=>{assert.throws(()=>validate(sample,{files:false}));assert.throws(()=>validate({...valid,APP_HOST:''},{files:false}));assert.equal(validate(valid,{files:false}),true);});
test('production preflight rejects origin/host injection, HTTP and local CA',()=>{for(const host of ['https://app.example.org','app.example.org/path','evil.example.org\nadmin off','localhost','app.test'])assert.throws(()=>validate({...valid,APP_HOST:host},{files:false}));assert.throws(()=>validate({...valid,TLS_CONFIG:'internal'},{files:false}));assert.throws(()=>validate({...valid,AUTH_HOST:valid.APP_HOST},{files:false}));});
test('preflight rejects malformed input and ambiguous platforms',()=>{assert.throws(()=>parseEnv('A=1\nA=2'));assert.throws(()=>validate({...valid,PLATFORM:'linux/386'},{files:false}));assert.throws(()=>validate({...valid,RELEASE_TAG:'bad tag'},{files:false}));});
test('production realm preserves auth flow and narrows transport and scopes',()=>{const r=JSON.parse(source('./workforce-realm.json'));const c=r.clients[0];assert.equal(r.sslRequired,'all');assert.equal(r.verifyEmail,true);assert.equal(r.bruteForceProtected,true);assert.equal(c.publicClient,false);assert.equal(c.fullScopeAllowed,false);assert.equal(c.directAccessGrantsEnabled,false);assert.equal(c.attributes['pkce.code.challenge.method'],'S256');assert.equal(c.redirectUris.length,2);assert.ok(c.redirectUris.every(v=>!v.includes('*')));});
test('deployment publishes only proxy and keeps legacy confirmation false',()=>{const s=source('./compose.yaml');assert.equal((s.match(/^\s+ports:/gm)||[]).length,1);assert.match(s,/LEGACY_TENANT_CONFIRMED: "false"/);assert.doesNotMatch(s,/start-dev|mailpit:/);assert.doesNotMatch(s,/^ {6}(POSTGRES_PASSWORD|NEXTAUTH_SECRET):/m);assert.match(s,/no-new-privileges/);assert.match(s,/STARTTLS_REQUIRED: "true"/);});
test('backup private key is restore-only and target is non-destructive',()=>{assert.doesNotMatch(source('./backup.compose.yaml'),/identity.txt/);assert.match(source('./restore.compose.yaml'),/identity.txt:ro/);const s=source('./backup.sh');assert.match(s,/Refusing non-empty database/);assert.match(s,/Refusing existing Mock SAP state/);assert.match(s,/pipefail/);assert.match(s,/age -R/);assert.match(s,/cmp "\$db.manifest" "\$db.restored"/);assert.doesNotMatch(s,/DROP DATABASE|dropdb|TRUNCATE/);});
test('all production image roots pin reviewed multiarch digests',()=>{const lock=JSON.parse(source('./images.lock.json'));for(const image of Object.values(lock)){assert.match(image.reference,/@sha256:[a-f0-9]{64}$/);assert.deepEqual(image.platforms,['linux/amd64','linux/arm64']);}for(const name of ['backend','frontend','mock-sap','caddy','keycloak','backup','postgres'])for(const line of source('./'+name+'.Dockerfile').split('\n').filter(v=>v.startsWith('FROM ')))assert.match(line,/@sha256:[a-f0-9]{64}/);});

test('Node build runtime remains consistent across development, CI and production images',()=>{
 const version=source('../../.nvmrc').trim();
 assert.match(version,/^24\.\d+\.\d+$/);
 const pinned=JSON.parse(source('./images.lock.json')).node.reference;
 assert.ok(pinned.startsWith(`node:${version}-bookworm-slim@sha256:`));
 for(const file of ['./frontend.Dockerfile','./mock-sap.Dockerfile','../../frontend/Dockerfile','../../frontend/tests/Dockerfile','../../mock-sap/Dockerfile']){
  const bases=source(file).split('\n').filter(line=>line.startsWith('FROM node:'));
  assert.ok(bases.length>0,file);
  for(const base of bases)assert.equal(base.split(' ')[1],pinned,file);
 }
 assert.equal(JSON.parse(source('../../frontend/package.json')).engines.node,`>=${version} <25`);
 for(const workflow of ['ci','e2e'])assert.match(source(`../../.github/workflows/${workflow}.yml`),/node-version-file: \.nvmrc/);
});

test('frontend uses the reviewed shell-free non-root runtime with an executable health probe',()=>{
 const lock=JSON.parse(source('./images.lock.json'));
 const dockerfile=source('./frontend.Dockerfile');
 assert.ok(dockerfile.includes('FROM '+lock.frontendRuntime.reference));
 assert.match(dockerfile,/USER 65532:65532/);
 assert.match(dockerfile,/ENTRYPOINT \["\/nodejs\/bin\/node","\/app\/frontend-entrypoint.mjs"\]/);
 assert.match(source('./compose.yaml'),/\["CMD", "\/nodejs\/bin\/node", "-e", "fetch\('http:\/\/127\.0\.0\.1:3000\/api\/health'/);
 assert.doesNotMatch(dockerfile,/with-secrets\.sh|USER node/);
});

test('readiness requires HTTP success, not a partially UP response body',()=>{const s=source('./compose.yaml');assert.doesNotMatch(s,/grep -q UP/);assert.equal((s.match(/HTTP\/1\.\[01\] 200/g)||[]).length,2);});

test('proxy preserves same-origin form Origin and removes incompatible file capabilities',()=>{assert.match(source('./Caddyfile'),/Referrer-Policy strict-origin-when-cross-origin/);assert.doesNotMatch(source('./Caddyfile'),/Referrer-Policy no-referrer/);assert.match(source('./caddy.Dockerfile'),/setcap -r \/usr\/bin\/caddy/);});
test('proxy sanitizes forwarding headers before assigning trusted values',()=>{
 const config=source('./Caddyfile');
 assert.match(config,/request_header -X-Forwarded-\*/);
 assert.doesNotMatch(config,/header_up -X-Forwarded-\*/);
 assert.match(config,/header_up X-Forwarded-Proto https/);
 assert.match(config,/header_up X-Forwarded-Host \{host\}/);
});
test('patched Caddy build pins compiler, modules and Brotli without a security bypass',()=>{
 const build=source('./caddy.Dockerfile');
 const go=JSON.parse(source('./images.lock.json')).go.reference;
 assert.ok(build.includes('FROM '+go));
 assert.match(build,/GOTOOLCHAIN=local/);assert.match(build,/-mod=readonly/);
 assert.match(build,/go mod download && go mod verify/);
 assert.doesNotMatch(build,/go get|go mod tidy|GOSUMDB=off/);
 assert.match(build,/brotli-libs=1\.2\.1-r0/);
 assert.match(source('./caddy/go.mod'),/golang\.org\/x\/net v0\.60\.0/);
 assert.match(source('./caddy/go.sum'),/github\.com\/caddyserver\/caddy\/v2 v2\.11\.7 h1:/);
});
test('backup may read private mock state but the normal mount is read-only',()=>{const s=source('./backup.compose.yaml');assert.match(s,/DAC_READ_SEARCH/);assert.match(s,/mock_sap_data:\/mock:ro/);});
test('database and backup rebuild the same helper while preserving PostgreSQL 17 root',()=>{
 const lock=JSON.parse(source('./images.lock.json'));
 assert.match(lock.postgres.reference,/^postgres:17-alpine@sha256:/);
 const database=source('./postgres.Dockerfile'),backup=source('./backup.Dockerfile');
 const split='FROM '+lock.postgres.reference;
 assert.ok(database.includes(split));assert.ok(backup.includes(split));
 assert.equal(database.split(split)[0],backup.split(split)[0]);
 for(const text of [database,backup]){
  assert.ok(text.includes('FROM '+lock.go.reference));
  assert.match(text,/-mod=readonly/);assert.match(text,/go mod download && go mod verify/);
  assert.match(text,/COPY --from=gosu-build \/out\/gosu \/usr\/local\/bin\/gosu/);
 }
 assert.doesNotMatch(database,/ENTRYPOINT|CMD|USER/,'Preserve upstream initialization and privilege drop');
 assert.match(source('./compose.yaml'),/dockerfile: infra\/production\/postgres.Dockerfile/);
 assert.match(source('./deploy-release.sh'),/PostgreSQL root must match reviewed source pin/);
});
