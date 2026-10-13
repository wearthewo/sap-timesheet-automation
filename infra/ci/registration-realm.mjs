import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {assertProject} from './support.mjs';
import {updateRegistration,registrationSettings} from '../keycloak/update-registration.mjs';

export async function verifyRegistrationRealm(project,env) {
 assertProject(project);
 const base=env.KEYCLOAK_PUBLIC_URL;
 const response=await fetch(base+'/realms/master/protocol/openid-connect/token',{method:'POST',body:new URLSearchParams({grant_type:'password',client_id:'admin-cli',username:env.KEYCLOAK_ADMIN_USERNAME,password:env.KEYCLOAK_ADMIN_PASSWORD})});
 if(!response.ok)throw Error('Realm verification authentication failed');
 const token=(await response.json()).access_token,headers={Authorization:`Bearer ${token}`,'Content-Type':'application/json'};
 async function api(path,method='GET',body){const r=await fetch(base+'/admin/'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});if(!r.ok)throw Error(`Isolated realm check HTTP ${r.status}`);return [201,204].includes(r.status)?null:r.json();}
 const current=await api('realms/workforce');
 for(const key of ['registrationAllowed','verifyEmail','resetPasswordAllowed','bruteForceProtected'])assert.equal(current[key],true);
 assert.equal(current.smtpServer.host,'mailpit');
 const realm='ci-upgrade-'+randomUUID();
 await api('realms','POST',{realm,enabled:true,registrationAllowed:false,verifyEmail:false,resetPasswordAllowed:false});
 try {
  await api(`realms/${realm}/clients`,'POST',{clientId:'workforce-web',enabled:true,protocol:'openid-connect',publicClient:false,standardFlowEnabled:true,directAccessGrantsEnabled:false,serviceAccountsEnabled:false,
   secret:env.KEYCLOAK_CLIENT_SECRET,redirectUris:[env.FRONTEND_URL+'/api/auth/callback/keycloak'],attributes:{'pkce.code.challenge.method':'S256'}});
  await api(`realms/${realm}/users`,'POST',{username:'preserved-user',email:'preserved@example.test',enabled:true,emailVerified:false});
  const before=await api(`realms/${realm}/users`),settings={...env,KEYCLOAK_REALM:realm};
  assert.equal((await updateRegistration(settings,false)).applied,false);
  assert.equal((await api(`realms/${realm}`)).registrationAllowed,false);
  await updateRegistration(settings,true);await updateRegistration(settings,true);
  const after=await api(`realms/${realm}/users`),configured=await api(`realms/${realm}`);
  assert.deepEqual(after,before); // IDs, email verification, profile and required actions unchanged.
  for(const [key,value] of Object.entries(registrationSettings(settings)))if(key!=='smtpServer')assert.deepEqual(configured[key],value);
  const [client]=await api(`realms/${realm}/clients?clientId=workforce-web`);
  assert.equal(client.redirectUris.length,2);assert.equal(new Set(client.redirectUris).size,2);
  const secret=await api(`realms/${realm}/clients/${client.id}/client-secret`);assert.equal(secret.value,env.KEYCLOAK_CLIENT_SECRET);
  console.log('PASS fresh realm registration settings; review-only update; repeatable existing-realm upgrade preserves users and client secret.');
 } finally { await api(`realms/${realm}`,'DELETE'); }
}
