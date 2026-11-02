// Disposable 12D harness. No reset/down command and no access to original volumes.
import {execFileSync,spawn} from 'node:child_process';
import {mkdirSync,readFileSync,writeFileSync,existsSync,createWriteStream} from 'node:fs';
import {resolve} from 'node:path';
import {randomBytes} from 'node:crypto';
import {secretNames,validate} from './validate-env.mjs';
const root=process.cwd(),dir=resolve(root,'.verification/phase12d'),stateFile=resolve(dir,'state.json');
const command=process.argv[2];let state;
function compose(args){return execFileSync('docker',['compose','-p',state.project,'--env-file',resolve(dir,'production.env'),'-f',resolve(root,'infra/production/compose.yaml'),...args],{encoding:'utf8',stdio:['pipe','pipe','pipe'],maxBuffer:32e6,timeout:720000});}
if(command==='prepare'){
 if(existsSync(stateFile))throw Error('Existing QA state: preserve it; do not reset');
 mkdirSync(resolve(dir,'secrets'),{recursive:true,mode:0o700});mkdirSync(resolve(dir,'backups'),{recursive:true});
 for(const name of secretNames)writeFileSync(resolve(dir,'secrets',name),randomBytes(32).toString('hex'),{mode:0o444});
 state={project:'workforce-prod-qa-'+randomBytes(5).toString('hex'),env:{APP_HOST:'app.workforce.test',AUTH_HOST:'auth.workforce.test',TLS_CONFIG:'internal',SECRET_DIR:resolve(dir,'secrets').replaceAll('\\','/'),BACKUP_DIR:resolve(dir,'backups').replaceAll('\\','/'),AGE_RECIPIENTS_FILE:resolve(dir,'recipients.txt').replaceAll('\\','/'),AGE_IDENTITY_FILE:resolve(dir,'restore-key.txt').replaceAll('\\','/'),SMTP_HOST:'smtp.disabled.test',SMTP_PORT:'587',SMTP_USERNAME:'synthetic-only',MAIL_FROM:'workforce@example.test',KEYCLOAK_ADMIN_USERNAME:'qa-operator',RELEASE_TAG:'phase12d-qa',PLATFORM:'linux/amd64',PUBLIC_BIND:'127.0.0.1',HTTP_PORT:'24080',HTTPS_PORT:'24443',INGRESS_SUBNET:'172.29.12.0/24',PROXY_IP:'172.29.12.254',BACKUP_RETENTION_DAYS:'14'}};
 writeFileSync(stateFile,JSON.stringify(state),{mode:0o600});writeFileSync(resolve(dir,'production.env'),Object.entries(state.env).map(([k,v])=>k+'='+v).join('\n'),{mode:0o600});
 validate(state.env,{local:true,files:false});console.log('Prepared isolated production-mode QA; no external SMTP.');
}else{
 state=JSON.parse(readFileSync(stateFile,'utf8'));if(!/^workforce-prod-qa-[a-f0-9]{10}$/.test(state.project))throw Error('Invalid QA project');
 if(command==='config'){compose(['config','--quiet']);console.log('Production Compose configuration valid');}
 else if(command==='build'){
  for(const service of ['postgres','mock-sap','proxy','keycloak','backend','frontend']){
   console.log('Building '+service);const out=createWriteStream(resolve(dir,'build-'+service+'.log'));
   const child=spawn('docker',['compose','-p',state.project,'--env-file',resolve(dir,'production.env'),'-f',resolve(root,'infra/production/compose.yaml'),'build',service],{stdio:['ignore','pipe','pipe']});
   child.stdout.pipe(out,{end:false});child.stderr.pipe(out,{end:false});const timer=setTimeout(()=>child.kill(),20*60*1000);const code=await new Promise((r,j)=>{child.once('error',j);child.once('close',r)});clearTimeout(timer);out.end();if(code!==0)throw Error('Build failed: '+service);console.log('Built '+service);
  }
 }else if(command==='start'){writeFileSync(resolve(dir,'start.log'),compose(['up','-d','--wait','--wait-timeout','600']));console.log('Six production-mode QA services healthy');}
 else if(command==='status'){console.log(compose(['ps']));}
 else throw Error('Commands: prepare, config, build, start, status');
}
