// Private synthetic QA probe. No SMTP transmission.
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const response=await fetch('http://keycloak:8080/realms/master/protocol/openid-connect/token',{method:'POST',body:new URLSearchParams({client_id:'admin-cli',grant_type:'password',username:'qa-operator',password:readFileSync('/qa/secrets/KEYCLOAK_ADMIN_PASSWORD','utf8').trim()})});
assert.equal(response.status,200);const token=(await response.json()).access_token;
const realm=await (await fetch('http://keycloak:8080/admin/realms/workforce',{headers:{Authorization:'Bearer '+token}})).json();
assert.equal(realm.sslRequired,'all');assert.equal(realm.verifyEmail,true);assert.equal(realm.smtpServer.starttls,'true');assert.equal(realm.smtpServer.auth,'true');assert.equal(realm.smtpServer.ssl,'false');assert.equal(realm.smtpServer.host,'smtp.disabled.test');
const clients=await (await fetch('http://keycloak:8080/admin/realms/workforce/clients?clientId=workforce-web',{headers:{Authorization:'Bearer '+token}})).json();
assert.equal(clients.length,1);assert.equal(clients[0].fullScopeAllowed,false);assert.ok(clients[0].redirectUris.every(u=>u.startsWith('https://app.workforce.test/api/auth/callback/')&&!u.includes('*')));
for(const path of ['/actuator/health/readiness','/actuator/health/liveness','/v3/api-docs','/swagger-ui/index.html'])assert.equal((await fetch('http://backend:8080'+path)).status,200,path);
assert.equal((await fetch('http://backend:8080/api/v1/dashboard')).status,401);
assert.equal((await fetch('http://mock-sap:8090/sap/timesheets/by-identity?identity=none')).status,401);
console.log('PASS: private readiness/liveness/OpenAPI/Swagger, unauthenticated API/SAP rejection, actual imported HTTPS realm/client and authenticated STARTTLS settings; no SMTP sent.');
