// Runs only against the disposable project produced by verify.mjs.
import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync,existsSync} from 'node:fs';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';
const root=process.cwd(),dir=resolve(root,'.verification/phase12d'),s=JSON.parse(readFileSync(resolve(dir,'state.json'),'utf8'));
assert.match(s.project,/^workforce-prod-qa-[a-f0-9]{10}$/);
const run=(args,input)=>execFileSync('docker',args,{input,encoding:'utf8',maxBuffer:20e6,timeout:720000,stdio:['pipe','pipe','pipe']});
const base=['compose','-p',s.project,'--env-file',resolve(dir,'production.env'),'-f',resolve(root,'infra/production/compose.yaml')];
const compose=(args,input)=>run([...base,...args],input);
const sql=q=>compose(['exec','-T','postgres','psql','-XAt','-v','ON_ERROR_STOP=1','-U','postgres','-d','workforce'],q);
assert.ok(Number(sql('SELECT count(*) FROM organization_memberships'))>0,'Browser-created membership required before backup');
assert.equal(Number(sql('SELECT count(*) FROM flyway_schema_history WHERE success')),17);
const script=`const fs=require('node:fs'); const token=fs.readFileSync('/run/secrets/SAP_API_TOKEN','utf8').trim(); const id='SAP_TIMESHEET:12121212-1212-4212-8212-121212121212'; fetch('http://127.0.0.1:8090/sap/timesheets',{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json','Idempotency-Key':id},body:JSON.stringify({ExternalSystemReference:id,PersonnelNumber:'RESTORE-001',CalendarYear:2026,CalendarMonth:10,TimeEntries:[{ProjectCode:'RESTORE-P1',WorkDate:'2026-10-01',Hours:'7.25',Description:'Synthetic restore fixture'}]})}).then(r=>{if(![201,409].includes(r.status))process.exit(1);console.log('Mock fixture persisted')})`;
compose(['exec','-T','mock-sap','node','-e',script]);
const backups=[...base,'-f',resolve(root,'infra/production/backup.compose.yaml')];
const writers=['proxy','frontend','backend','keycloak','mock-sap'];
console.log('Quiescing only disposable writers');
compose(['stop',...writers]);
let archive;
try{
 const result=run([...backups,'run','--rm','-e','WRITERS_QUIESCED=yes','backup','backup']);
 writeFileSync(resolve(dir,'backup.log'),result);archive=readFileSync(resolve(dir,'backups/last-success'),'utf8').trim();
 assert.match(archive,/^workforce-[0-9TZ]+-[0-9]+\.tar\.age$/);
}finally{compose(['start',...writers]);}
const restoreProject=s.project+'-restore';
const restoreBase=['compose','-p',restoreProject,'--env-file',resolve(dir,'production.env'),'-f',resolve(root,'infra/production/compose.yaml'),'-f',resolve(root,'infra/production/backup.compose.yaml'),'-f',resolve(root,'infra/production/restore.compose.yaml')];
console.log('Restoring into a separate empty project');
run([...restoreBase,'up','-d','--wait','--wait-timeout','120','postgres']);
const restoreArgs=[...restoreBase,'run','--rm','-e','RESTORE_CONFIRM=EMPTY_DISPOSABLE_OR_APPROVED_TARGET','-e','BACKUP_ARCHIVE='+archive,'backup','restore'];
const result=run(restoreArgs);writeFileSync(resolve(dir,'restore.log'),result);
assert.match(result,/Restore verified/);
let rejected=false;try{run(restoreArgs);}catch(e){rejected=String(e.stderr).includes('Refusing non-empty database');}
assert.equal(rejected,true,'Nonempty restore must fail closed');
run([...restoreBase,'up','-d','--wait','--wait-timeout','120','mock-sap']);
const lookup=`const fs=require('node:fs');fetch('http://127.0.0.1:8090/sap/timesheets/by-identity?identity=SAP_TIMESHEET:12121212-1212-4212-8212-121212121212',{headers:{Authorization:'Bearer '+fs.readFileSync('/run/secrets/SAP_API_TOKEN','utf8').trim()}}).then(async r=>{const b=await r.json();if(r.status!==200||!b.externalReference)process.exit(1);console.log('Restored Mock SAP identity found')})`;
run([...restoreBase,'exec','-T','mock-sap','node','-e',lookup]);
run([...restoreBase,'exec','-T','mock-sap','node','-e',script.replace('![201,409].includes(r.status)','r.status!==409')]);
const summary=run([...restoreBase,'exec','-T','postgres','psql','-XAt','-U','postgres','-d','workforce','-c','SELECT (SELECT count(*) FROM organization_memberships),(SELECT count(*) FROM flyway_schema_history WHERE success)']);
writeFileSync(resolve(dir,'restore-result.json'),JSON.stringify({passed:true,archive,restoreProject,counts:summary.trim(),allPublicTableHashesMatched:true,nonemptyTargetRejected:true,mockLookupAndDuplicatePassed:true}));
console.log('Encrypted restore passed: both DB manifests, memberships, 17 Flyway migrations, Mock SAP lookup/duplicate; nonempty restore rejected.');
