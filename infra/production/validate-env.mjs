import {readFileSync,statSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
export const secretNames=['PG_ADMIN_PASSWORD','DATABASE_PASSWORD','KEYCLOAK_DB_PASSWORD','KEYCLOAK_ADMIN_PASSWORD','KEYCLOAK_CLIENT_SECRET','NEXTAUTH_SECRET','SAP_API_TOKEN','INVITATION_SIGNING_KEY','SMTP_PASSWORD'];
export function parseEnv(text){
 const env={};for(const line of text.split(/\r?\n/)){if(!line.trim()||line.startsWith('#'))continue;const match=line.match(/^([A-Z][A-Z0-9_]*)=(.*)$/);if(!match||Object.hasOwn(env,match[1]))throw Error('Invalid or duplicate environment line');env[match[1]]=match[2];}return env;
}
export function validate(env,{local=false,files=true}={}){
 const required=['APP_HOST','AUTH_HOST','TLS_CONFIG','SECRET_DIR','BACKUP_DIR','AGE_RECIPIENTS_FILE','SMTP_HOST','SMTP_USERNAME','MAIL_FROM','KEYCLOAK_ADMIN_USERNAME','RELEASE_TAG','PLATFORM'];
 for(const k of required)if(!env[k]||/REPLACE|CHANGEME/.test(env[k])||/[\r\n]/.test(env[k]))throw Error('Missing/placeholder setting: '+k);
 for(const k of ['APP_HOST','AUTH_HOST','SMTP_HOST'])if(!/^(?=.{1,253}$)[a-z0-9]+(?:[.-][a-z0-9]+)+$/.test(env[k])||(!local&&/\.(invalid|test|localhost)$/.test(env[k])))throw Error('Invalid hostname: '+k);
 if(env.APP_HOST===env.AUTH_HOST)throw Error('Use distinct app and identity hosts');
 if(!/^linux\/(amd64|arm64)$/.test(env.PLATFORM))throw Error('Unsupported platform');
 if(!/^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,100}$/.test(env.RELEASE_TAG))throw Error('Invalid release tag');
 if(env.TLS_CONFIG==='internal'?!local:! /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(env.TLS_CONFIG))throw Error('Invalid TLS contact');
 if(!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(env.MAIL_FROM))throw Error('Invalid sender');
 if(!local&&(/\.invalid$/.test(env.TLS_CONFIG)||/\.invalid$/.test(env.MAIL_FROM)))throw Error('Placeholder email');
 if(files){
  for(const k of secretNames){const path=resolve(env.SECRET_DIR,k);const value=readFileSync(path,'utf8').trim();if(value.length<32||/REPLACE|CHANGEME/.test(value))throw Error('Missing/weak secret: '+k);}
  if(!readFileSync(env.AGE_RECIPIENTS_FILE,'utf8').split(/\r?\n/).some(x=>/^age1[0-9a-z]+$/.test(x)))throw Error('Missing age recipient');
  if(process.platform!=='win32'&&(statSync(env.SECRET_DIR).mode&0o077)!==0)throw Error('Secret directory must be mode 0700');
 }
 return true;
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{const i=process.argv.indexOf('--env-file');if(i<0)throw Error('Use --env-file <path> [--local-test]');validate(parseEnv(readFileSync(process.argv[i+1],'utf8')),{local:process.argv.includes('--local-test')});console.log('Environment and required secret files validated; no values printed.');}
 catch(e){console.error(e.message);process.exitCode=1;}
}
