// Explicit, disposable component checks. Never opens an existing database or volume.
// Usage: node infra/production/runtime-candidate-check.mjs proxy|postgres sha256:<local-image-id>
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {mkdirSync, writeFileSync, readFileSync, unlinkSync} from 'node:fs';
import {resolve} from 'node:path';
import {randomBytes} from 'node:crypto';
import https from 'node:https';
import http from 'node:http';
import http2 from 'node:http2';
import tls from 'node:tls';
const [kind,image,liveOption]=process.argv.slice(2);
assert.ok(liveOption===undefined||liveOption==='--live-readonly');
const live=liveOption==='--live-readonly';
assert.ok(['proxy','postgres'].includes(kind));
assert.match(image??'',/^sha256:[a-f0-9]{64}$/,'Use an inspected immutable local image ID');
const root=process.cwd();
const project='workforce-runtime-check-'+randomBytes(6).toString('hex');
const dir=resolve(root,'.verification/phase13-remediation',project);
mkdirSync(dir,{recursive:true});
const docker=(args)=>execFileSync('docker',args,{encoding:'utf8',timeout:60000,maxBuffer:8e6});
const source=JSON.parse(docker(['compose','--env-file','infra/production/production.env.example','-f','infra/production/compose.yaml','config','--format','json']));
const metadata=JSON.parse(docker(['image','inspect',image]))[0];
assert.equal(metadata.Architecture,'amd64');assert.equal(metadata.Os,'linux');
const service=structuredClone(source.services[kind]);
// Preserve production command, user, limits, security options, health and init behavior.
for(const key of ['build','depends_on','networks','volumes','secrets','ports'])delete service[key];
service.image=image;service.pull_policy='never';service.restart='no';
const config={services:{[kind]:service}};
const secretFiles=[];
if(kind==='proxy'){
 service.environment={APP_HOST:'app.security.test',AUTH_HOST:'auth.security.test',TLS_CONFIG:'internal'};
 service.tmpfs=[...(service.tmpfs??[]),'/data:uid=1000,gid=1000,mode=0700,size=16m','/config:uid=1000,gid=1000,mode=0700,size=16m'];
 service.ports=[{target:80,published:'0',host_ip:'127.0.0.1'},{target:443,published:'0',host_ip:'127.0.0.1'}];
 const node=JSON.parse(readFileSync('infra/production/images.lock.json','utf8')).node.reference;
 // Optional public GET probes only: no cookie, token, mutation or login to existing QA.
 const fixture=`require('node:http').createServer(async(q,s)=>{if(process.env.LIVE==='true'&&q.method==='GET'&&['/login','/api/health','/realms/workforce/.well-known/openid-configuration'].includes(q.url)){try{const port=q.url.startsWith('/realms/')?38180:33000;const r=await fetch('http://host.docker.internal:'+port+q.url,{redirect:'manual',signal:AbortSignal.timeout(8000)});s.writeHead(r.status,{'content-type':r.headers.get('content-type')||'text/plain'});s.end(await r.text());}catch{s.writeHead(502);s.end('Read-only QA probe failed');}return;}let n=0;q.on('data',b=>n+=b.length);q.on('error',()=>{});q.on('end',()=>{s.setHeader('content-type','application/json');s.end(JSON.stringify({headers:q.headers,path:q.url,bytes:n}))})}).listen(Number(process.env.PORT),'0.0.0.0')`;
 for(const [name,port] of [['frontend',3000],['keycloak',8080]])config.services[name]={image:node,pull_policy:'never',platform:'linux/amd64',read_only:true,user:'1000:1000',cap_drop:['ALL'],security_opt:['no-new-privileges:true'],mem_limit:'128m',environment:{PORT:String(port),LIVE:String(live)},command:['node','-e',fixture]};
}else{
 service.tmpfs=['/var/lib/postgresql/data:size=512m,mode=0700'];
 config.secrets={};service.secrets=[];
 for(const name of ['PG_ADMIN_PASSWORD','DATABASE_PASSWORD','KEYCLOAK_DB_PASSWORD']){
  const file=resolve(dir,name);writeFileSync(file,randomBytes(24).toString('hex'),{mode:0o600});secretFiles.push(file);
  config.secrets[name]={file};service.secrets.push(name);
 }
 service.volumes=[{type:'bind',source:resolve(root,'infra/production/postgres-init.sh'),target:'/docker-entrypoint-initdb.d/01-databases.sh',read_only:true}];
 config.networks={default:{internal:true}};
}
const file=resolve(dir,'compose.json');writeFileSync(file,JSON.stringify(config,null,2));
const compose=args=>docker(['compose','-p',project,'-f',file,...args]);
const checks=[];
const pass=name=>{checks.push(name);console.log('PASS: '+name);};
try{
 compose(['config','--quiet']);
 compose(['up','-d','--no-build','--wait','--wait-timeout','45']);
 pass('production component health under original limits and security options');
 const id=compose(['ps','-q',kind]).trim();
 if(kind==='postgres'){
  const sql=q=>compose(['exec','-T','postgres','psql','-XAt','-U','postgres','-v','ON_ERROR_STOP=1','-c',q]).trim();
  assert.equal(sql("SELECT string_agg(datname, ',' ORDER BY datname) FROM pg_database WHERE datname IN ('workforce','keycloak')"),'keycloak,workforce');
  assert.equal(sql("SELECT count(*) FROM pg_roles WHERE rolname IN ('workforce','keycloak') AND NOT rolsuper"),'2');
  pass('unchanged production initializer creates separate least-privileged databases');
  assert.equal(compose(['exec','-T','postgres','gosu','postgres','id','-un']).trim(),'postgres');
  assert.equal(sql('SHOW shared_buffers'),'256MB');assert.equal(sql('SHOW max_connections'),'80');
  pass('patched gosu drops privileges and PostgreSQL preserves production settings');
  sql('CREATE TABLE runtime_probe (id int PRIMARY KEY, hours numeric(6,2)); INSERT INTO runtime_probe VALUES (1, 7.25)');
  compose(['exec','-T','postgres','sh','-ec','pg_dump -U postgres -Fc -t runtime_probe postgres > /tmp/probe.dump; createdb -U postgres restored; pg_restore -U postgres -d restored /tmp/probe.dump']);
  assert.equal(compose(['exec','-T','postgres','psql','-XAt','-U','postgres','-d','restored','-c','SELECT hours FROM runtime_probe']).trim(),'7.25');
  pass('pg_dump/pg_restore preserves decimal data in disposable tmpfs database');
 }else{
  assert.equal(metadata.Config.User,'1000:1000');
  assert.equal(compose(['exec','-T','proxy','id','-u']).trim(),'1000');
  const certificate=compose(['exec','-T','proxy','cat','/data/caddy/pki/authorities/local/root.crt']);
  const port=Number(compose(['port','proxy','443']).trim().split(':').at(-1));
  const clearPort=Number(compose(['port','proxy','80']).trim().split(':').at(-1));
  const request=(host,path,options={})=>new Promise((resolve,reject)=>{
   const {clear=false,body='',...rest}=options;
   const req=(clear?http:https).request({hostname:'127.0.0.1',port:clear?clearPort:port,servername:host,ca:certificate,path,method:body?'POST':'GET',...rest,headers:{Host:host,...rest.headers}},res=>{let data='';res.on('data',b=>data+=b);res.on('end',()=>resolve({status:res.statusCode,headers:res.headers,data}));});
   req.setTimeout(10000,()=>req.destroy(Error('request timeout')));req.on('error',reject);req.end(body);
  });
  const redirect=await request('app.security.test','/login',{clear:true});assert.equal(redirect.status,308);assert.equal(redirect.headers.location,'https://app.security.test/login');
  const response=await request('app.security.test','/probe',{headers:{Origin:'https://app.security.test','X-Forwarded-For':'203.0.113.123','X-Forwarded-Proto':'http',Forwarded:'for=evil','X-Real-IP':'evil','traceparent':'evil'}});
  assert.equal(response.status,200);const headers=JSON.parse(response.data).headers;
  assert.equal(headers.origin,'https://app.security.test');assert.equal(headers['x-forwarded-proto'],'https');assert.equal(headers['x-forwarded-host'],'app.security.test');assert.notEqual(headers['x-forwarded-for'],'203.0.113.123');
  for(const h of ['forwarded','x-real-ip','traceparent'])assert.equal(headers[h],undefined);
  assert.equal(response.headers['strict-transport-security'],'max-age=31536000');assert.equal(response.headers['referrer-policy'],'strict-origin-when-cross-origin');assert.equal(response.headers.server,undefined);
  pass('trusted TLS, canonical HTTPS redirect, same-origin forwarding and spoof stripping');
  assert.equal((await request('auth.security.test','/realms/workforce/probe')).status,200);
  assert.equal((await request('auth.security.test','/admin')).status,404);
  assert.equal((await request('app.security.test','/large',{body:'x'.repeat(200000)})).status,413);
  pass('Keycloak route boundary and unchanged body-size limit');
  if(live){
   const login=await request('app.security.test','/login');assert.equal(login.status,200);assert.match(login.headers['content-type'],/text\/html/);
   assert.equal((await request('app.security.test','/api/health')).status,200);
   const discovery=await request('auth.security.test','/realms/workforce/.well-known/openid-configuration');assert.equal(discovery.status,200);
   const oidc=JSON.parse(discovery.data);assert.ok(oidc.authorization_endpoint.endsWith('/protocol/openid-connect/auth'));assert.ok(oidc.issuer.endsWith('/realms/workforce'));
   pass('read-only real QA login/health and Keycloak discovery through candidate TLS proxy');
  }
  await new Promise((resolve,reject)=>{
   const client=http2.connect('https://app.security.test',{createConnection:()=>tls.connect({host:'127.0.0.1',port,servername:'app.security.test',ca:certificate,ALPNProtocols:['h2']})});
   client.on('error',reject);client.setTimeout(10000,()=>{client.destroy();reject(Error('HTTP2 timeout'));});
   const req=client.request({':path':'/http2'});let status;req.on('response',h=>status=h[':status']);req.on('data',()=>{});req.on('error',reject);req.on('end',()=>{client.close();try{assert.equal(status,200);resolve();}catch(e){reject(e);}});req.end();
  });
  pass('HTTP/2 with certificate verification');
 }
 compose(['stop','-t','15',kind]);
 const stopped=JSON.parse(docker(['inspect',id]))[0];assert.equal(stopped.State.ExitCode,0);assert.equal(stopped.State.OOMKilled,false);
 pass('graceful shutdown without OOM');
 writeFileSync(resolve(dir,'result.json'),JSON.stringify({project,image,kind,checks,passed:checks.length},null,2));
}finally{
 try{writeFileSync(resolve(dir,'containers.log'),compose(['logs','--no-color']));}finally{
  // Exact newly generated project only; no named volumes exist and no --volumes flag.
  compose(['down','--timeout','15']);
  for(const secret of secretFiles)unlinkSync(secret);
 }
}
