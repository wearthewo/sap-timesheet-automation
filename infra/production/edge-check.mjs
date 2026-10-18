import assert from 'node:assert/strict';
const base='https://app.workforce.test';
const redirect=await fetch('http://app.workforce.test/login',{redirect:'manual'});
assert.equal(redirect.status,308);assert.equal(redirect.headers.get('location'),base+'/login');
for(const path of ['/actuator/health','/v3/api-docs','/sap/timesheets'])assert.equal((await fetch(base+path)).status,404);
const oversized=await fetch(base+'/api/invitations/context',{method:'POST',headers:{Origin:base,'Content-Type':'application/json'},body:JSON.stringify({token:'x'.repeat(200000)})});assert.equal(oversized.status,413);
const response=await fetch(base+'/login?probe=phase12d-private-query-marker',{headers:{'X-Forwarded-For':'203.0.113.123','X-Forwarded-Host':'evil.example','X-Forwarded-Proto':'http'}});
assert.equal(response.status,200);assert.ok(response.headers.get('x-request-id'));assert.equal(response.headers.get('referrer-policy'),'strict-origin-when-cross-origin');
console.log('PASS: HTTP308 to canonical HTTPS, private paths404, oversized body413, spoofed forwarding headers do not alter public login.');
