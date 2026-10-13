// Controlled local-realm update. Never re-import/delete the realm or modify its users.
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';

export function registrationSettings(env) {
 return {registrationAllowed:true,verifyEmail:true,resetPasswordAllowed:true,bruteForceProtected:true,permanentLockout:false,failureFactor:5,
  passwordPolicy:'length(12) and notUsername(undefined) and notEmail(undefined)',
  smtpServer:{host:env.KEYCLOAK_SMTP_HOST||'mailpit',port:env.KEYCLOAK_SMTP_PORT||'1025',from:env.KEYCLOAK_SMTP_FROM||'accounts@workforce.local',fromDisplayName:'Workforce accounts',
   auth:env.KEYCLOAK_SMTP_AUTH||'false',starttls:env.KEYCLOAK_SMTP_STARTTLS||'false',ssl:env.KEYCLOAK_SMTP_SSL||'false',user:env.KEYCLOAK_SMTP_USERNAME||'',password:env.KEYCLOAK_SMTP_PASSWORD||''}};
}
export async function updateRegistration(env,apply=false) {
 const base=new URL(env.KEYCLOAK_PUBLIC_URL||'http://localhost:8180');
 if(!['localhost','127.0.0.1','[::1]'].includes(base.hostname))throw Error('This helper is restricted to local Keycloak. Review production SMTP/security settings separately.');
 const realm=env.KEYCLOAK_REALM||'workforce';
 const frontend=new URL(env.FRONTEND_URL||'http://localhost:3000');
 if(!['http:','https:'].includes(frontend.protocol)||frontend.pathname!=='/'||frontend.search||frontend.hash||frontend.username||frontend.password)throw Error('FRONTEND_URL must be an origin');
 const tokenResponse=await fetch(new URL('/realms/master/protocol/openid-connect/token',base),{method:'POST',body:new URLSearchParams({grant_type:'password',client_id:'admin-cli',username:env.KEYCLOAK_ADMIN_USERNAME||'admin',password:env.KEYCLOAK_ADMIN_PASSWORD||''}),signal:AbortSignal.timeout(30000)});
 if(!tokenResponse.ok)throw Error('Local administrator authentication failed');
 const token=(await tokenResponse.json()).access_token;
 const headers={Authorization:`Bearer ${token}`,'Content-Type':'application/json'};
 async function api(path,method='GET',body){const r=await fetch(new URL(`/admin/realms/${encodeURIComponent(realm)}${path}`,base),{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});if(!r.ok)throw Error(`Local realm operation failed: HTTP ${r.status}`);return r.status===204?null:r.json();}
 const current=await api(''),desired=registrationSettings(env);
 const clients=await api('/clients?clientId='+encodeURIComponent(env.KEYCLOAK_CLIENT_ID||'workforce-web'));
 if(clients.length!==1)throw Error('Expected one Workforce client');
 const client=clients[0],callback=frontend.origin+'/api/auth/callback/keycloak-register';
 if(!client.standardFlowEnabled||client.publicClient||client.directAccessGrantsEnabled||client.serviceAccountsEnabled||client.attributes?.['pkce.code.challenge.method']!=='S256')throw Error('Existing client must retain confidential code flow, PKCE and disabled password/service grants');
 const changed=Object.keys(desired).filter(k=>JSON.stringify(current[k])!==JSON.stringify(desired[k]));
 const redirectMissing=!(client.redirectUris||[]).includes(callback);
 if(apply){
  // Partial realm representation changes only these settings; no users, credentials, roles or client secrets.
  if(changed.length)await api('','PUT',desired);
  if(redirectMissing)await api('/clients/'+encodeURIComponent(client.id),'PUT',{redirectUris:[...client.redirectUris,callback]});
 }
 return {realm,applied:apply,settingsChanged:changed,registrationCallbackAdded:redirectMissing};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 try {
  const arg=process.argv.indexOf('--env-file');if(arg<0||!process.argv[arg+1])throw Error('Use --env-file <local env file> [--apply]; default is review only');
  const env=Object.fromEntries(readFileSync(process.argv[arg+1],'utf8').split(/\r?\n/).filter(l=>/^[A-Z_]+=/.test(l)).map(l=>[l.slice(0,l.indexOf('=')),l.slice(l.indexOf('=')+1)]));
  console.log(JSON.stringify(await updateRegistration(env,process.argv.includes('--apply')),null,2));
 }catch{console.error('Realm update failed. Check local URL, administrator credentials, realm/client configuration and service readiness. No raw response or credentials are logged.');process.exitCode=1;}
}
