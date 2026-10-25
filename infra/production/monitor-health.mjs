import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {parseEnv,validate} from './validate-env.mjs';
const counters=['sapFailed','sapUnknown','notificationFailed','sapOverdue','notificationOverdue','sapExpiredLeases','notificationExpiredLeases','migrationFailures'];
export function checkCounters(value){
 assert.deepEqual(Object.keys(value).sort(),[...counters].sort(),'Incomplete operational snapshot');
 for(const key of counters)assert.ok(Number.isSafeInteger(value[key])&&value[key]>=0,'Invalid operational counter');
 return counters.filter(key=>value[key]>0).map(key=>`${key}=${value[key]}`);
}
export async function checkHttp(env,request=fetch){
 validate(env,{files:false});
 // Public TLS/hostname checks, no bearer credentials, redirects or insecure fallback.
 for(const [url,issuer] of [
  [`https://${env.APP_HOST}/api/health`,null],
  [`https://${env.AUTH_HOST}/realms/workforce/.well-known/openid-configuration`,`https://${env.AUTH_HOST}/realms/workforce`]
 ]){
  const response=await request(url,{redirect:'error',signal:AbortSignal.timeout(10000)});
  assert.equal(response.status,200,'Public HTTPS probe failed');
  if(issuer)assert.equal((await response.json()).issuer,issuer,'Unexpected OIDC issuer');
  else await response.body?.cancel();
 }
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{
  if(process.argv[2]==='jobs'){
   const alerts=checkCounters(JSON.parse(readFileSync(0,'utf8')));
   if(alerts.length){console.error('ALERT: '+alerts.join(', '));process.exitCode=1;}
  }else if(process.argv[2]==='http')await checkHttp(parseEnv(readFileSync(process.argv[3],'utf8')));
  else throw Error('Invalid monitor operation');
 }catch{console.error('ALERT: operational probe failed; inspect health/configuration without logging secrets');process.exitCode=1;}
}
