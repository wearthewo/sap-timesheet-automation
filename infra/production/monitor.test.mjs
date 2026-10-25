import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {checkCounters,checkHttp} from './monitor-health.mjs';
import {parseEnv} from './validate-env.mjs';
const healthy=()=>Object.fromEntries(['sapFailed','sapUnknown','notificationFailed','sapOverdue','notificationOverdue','sapExpiredLeases','notificationExpiredLeases','migrationFailures'].map(k=>[k,0]));
test('empty healthy queues pass; each failure/backlog/expired lease alerts',()=>{
 assert.deepEqual(checkCounters(healthy()),[]);
 for(const key of Object.keys(healthy()))assert.deepEqual(checkCounters({...healthy(),[key]:1}),[`${key}=1`]);
});
test('missing, negative or malformed operational counters fail closed',()=>{
 for(const value of [{},{...healthy(),sapFailed:-1},{...healthy(),sapUnknown:'0'},{...healthy(),unexpected:0}])assert.throws(()=>checkCounters(value));
});
const env={...parseEnv(readFileSync(new URL('./production.env.example',import.meta.url),'utf8')),APP_HOST:'app.example.org',AUTH_HOST:'auth.example.org',SMTP_HOST:'smtp.example.org',SMTP_USERNAME:'sender',MAIL_FROM:'sender@example.org',TLS_CONFIG:'operator@example.org',KEYCLOAK_ADMIN_USERNAME:'operator'};
test('HTTPS probes verify both public routes and exact issuer with bounded time and no redirects',async()=>{
 const calls=[];await checkHttp(env,async(url,options)=>{calls.push(url);assert.equal(options.redirect,'error');assert.ok(options.signal);return {status:200,json:async()=>({issuer:'https://auth.example.org/realms/workforce'})};});
 assert.deepEqual(calls,['https://app.example.org/api/health','https://auth.example.org/realms/workforce/.well-known/openid-configuration']);
});
test('unhealthy HTTP, incorrect issuer and network/TLS errors fail the pulse',async()=>{
 for(const request of [async()=>({status:503}),async()=>({status:200,json:async()=>({issuer:'https://wrong.example.org'})}),async()=>{throw Error('TLS failure');}])await assert.rejects(checkHttp(env,request));
});
