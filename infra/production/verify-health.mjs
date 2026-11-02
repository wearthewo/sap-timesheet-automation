import {execFileSync,spawnSync} from 'node:child_process';
import {readFileSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';
const dir=resolve('.verification/phase12d'),s=JSON.parse(readFileSync(resolve(dir,'state.json'),'utf8'));
assert.match(s.project,/^workforce-prod-qa-[a-f0-9]{10}$/);
const docker=args=>execFileSync('docker',args,{encoding:'utf8',timeout:60000,maxBuffer:20e6});
const names=['proxy','frontend','backend','keycloak','postgres','mock-sap'].map(n=>s.project+'-'+n+'-1');
const states=JSON.parse(docker(['inspect',...names]));
const secretNames=['PG_ADMIN_PASSWORD','DATABASE_PASSWORD','KEYCLOAK_DB_PASSWORD','KEYCLOAK_ADMIN_PASSWORD','KEYCLOAK_CLIENT_SECRET','NEXTAUTH_SECRET','SAP_API_TOKEN','INVITATION_SIGNING_KEY','SMTP_PASSWORD'];
const secrets=secretNames.map(n=>readFileSync(resolve(dir,'secrets',n),'utf8').trim());
for(const c of states){
 assert.equal(c.State.Health.Status,'healthy',c.Name);assert.equal(c.State.OOMKilled,false);
 const ports=Object.values(c.NetworkSettings.Ports??{}).filter(Boolean);
 if(!c.Name.includes('-proxy-'))assert.equal(ports.length,0,c.Name);
 if(!c.Name.includes('-postgres-'))assert.notEqual(c.Config.User,'0');
 const output=spawnSync('docker',['logs',c.Id],{encoding:'utf8',maxBuffer:20e6});assert.equal(output.status,0);const logs=output.stdout+output.stderr;
 for(const secret of secrets){assert.ok(!logs.includes(secret),'No secret in container logs');assert.ok(!JSON.stringify(c.Config.Env).includes(secret),'No secret values in container config');}
}
const stats=docker(['stats','--no-stream','--format','{{json .}}',...names]);
writeFileSync(resolve(dir,'health-resources.json'),JSON.stringify({containers:states.map(c=>({name:c.Name,health:c.State.Health.Status,restarts:c.RestartCount,oom:c.State.OOMKilled,user:c.Config.User,ports:c.NetworkSettings.Ports})),stats:stats.trim().split('\n').map(JSON.parse)},null,2));
console.log('PASS: six healthy services, no OOM, only proxy publishes ports; runtime secrets absent from Config.Env/container logs. Resource sample saved (not a load benchmark).');
