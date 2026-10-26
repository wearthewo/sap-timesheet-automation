// Creates/removes only its own synthetic container and explicitly labeled volume.
import {execFileSync} from 'node:child_process';
import {randomBytes,randomUUID} from 'node:crypto';
import {mkdirSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';
const docker=args=>execFileSync('docker',args,{encoding:'utf8',timeout:120000});
const name='workforce-mock-check-'+randomBytes(5).toString('hex'),volume=name+'-data';
const dir=resolve('.verification/private-demo',name);mkdirSync(dir,{recursive:true});
const secret=resolve(dir,'token'),token=randomBytes(32).toString('hex');writeFileSync(secret,token,{mode:0o600});
const image=process.env.MOCK_CANDIDATE_IMAGE??'workforce-mock-sap:distroless-candidate';
const imageId=JSON.parse(docker(['image','inspect',image]))[0].Id;
const start=()=>docker(['run','-d','--name',name,'--label','workforce.check='+name,'--read-only','--cap-drop','ALL','--security-opt','no-new-privileges:true','--memory','128m','--tmpfs','/tmp:size=16m','-p','127.0.0.1::8090','--mount',`type=bind,src=${secret},dst=/run/secrets/SAP_API_TOKEN,readonly`,'--mount',`type=volume,src=${volume},dst=/data`,'-e','SECRET_NAMES=SAP_API_TOKEN',imageId]);
let base;
async function ready(){
 base='http://127.0.0.1:'+JSON.parse(docker(['inspect',name]))[0].NetworkSettings.Ports['8090/tcp'][0].HostPort;
 const deadline=Date.now()+20000;
 while(Date.now()<deadline){try{if((await fetch(base+'/health')).ok)return;}catch{}await new Promise(r=>setTimeout(r,100));}
 throw Error('Candidate health deadline exceeded');
}
const identity='SAP_TIMESHEET:'+randomUUID();
const payload={ExternalSystemReference:identity,PersonnelNumber:'SYNTHETIC',CalendarYear:2026,CalendarMonth:10,TimeEntries:[{ProjectCode:'DEMO',WorkDate:'2026-10-01',Hours:'6.25',Description:'Synthetic runtime check'}]};
const request=(path,options={})=>fetch(base+path,{...options,headers:{Authorization:'Bearer '+token,...options.headers}});
try{
 docker(['volume','create','--label','workforce.check='+name,volume]);start();await ready();
 assert.equal((await fetch(base+'/sap/timesheets')).status,401);
 let r=await request('/sap/timesheets',{method:'POST',headers:{'content-type':'application/json','idempotency-key':identity},body:JSON.stringify(payload)});assert.equal(r.status,201);const accepted=await r.json();
 r=await request('/sap/timesheets',{method:'POST',headers:{'content-type':'application/json','idempotency-key':identity},body:JSON.stringify(payload)});assert.equal(r.status,409);
 docker(['exec',name,'/nodejs/bin/node','-e',`const fs=require('fs');if(process.getuid()!==1000)process.exit(1);const s=fs.statSync('/data/timesheets.json');if(s.uid!==1000||(s.mode&0o077)!==0)process.exit(2);try{fs.writeFileSync('/app/blocked','x');process.exit(3)}catch(e){if(e.code!=='EROFS')throw e}`]);
 docker(['stop','-t','25',name]);const stopped=JSON.parse(docker(['inspect',name]))[0];assert.equal(stopped.State.ExitCode,0);assert.equal(stopped.State.OOMKilled,false);docker(['rm',name]);
 start();await ready();r=await request('/sap/timesheets/by-identity?identity='+encodeURIComponent(identity));assert.equal(r.status,200);assert.equal((await r.json()).externalReference,accepted.externalReference);
 const invalid=structuredClone(payload);invalid.ExternalSystemReference='SAP_TIMESHEET:'+randomUUID();invalid.TimeEntries[0].ProjectCode='MOCK-INVALID';
 r=await request('/sap/timesheets',{method:'POST',headers:{'content-type':'application/json','idempotency-key':invalid.ExternalSystemReference},body:JSON.stringify(invalid)});assert.equal(r.status,400);
 writeFileSync(resolve(dir,'result.json'),JSON.stringify({imageId,passed:true,checks:['health','unauthorized denial','create','duplicate identity','UID 1000 and private state file','read-only root','SIGTERM exit 0','restart persisted lookup','validation error']},null,2));
 console.log('PASS: 9 Mock SAP runtime checks; '+imageId);
}finally{
 const containers=docker(['ps','-aq','--filter','label=workforce.check='+name]).trim();
 if(containers){docker(['stop','-t','25',name]);docker(['rm',name]);}
 const v=JSON.parse(docker(['volume','inspect',volume]))[0];assert.equal(v.Labels['workforce.check'],name);docker(['volume','rm',volume]);
}
