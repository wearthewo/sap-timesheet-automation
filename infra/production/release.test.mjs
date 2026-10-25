import {test} from 'node:test';
import assert from 'node:assert/strict';
import {services,validateRelease,composeRelease} from './release.mjs';
import {assertApproval,assertVerification,assertJobs} from './release-gates.mjs';
import {assertScan} from './scan-verification.mjs';
const sha='a'.repeat(40),digest='b'.repeat(64);
const release=()=>({schema:1,revision:sha,platform:'linux/amd64',images:Object.fromEntries(services.map(s=>[s,`ghcr.io/wearthewo/sap-timesheet-automation/${s}@sha256:${digest}`]))});
test('release uses only exact AMD64 immutable image inventory',()=>{
 const value=release();assert.equal(validateRelease(value),value);
 const overlay=composeRelease(value);assert.equal(overlay.services.backend.image,value.images.backend);
 assert.equal(overlay.services.backup.profiles[0],'operations');assert.equal(overlay.services.backend.pull_policy,'never');
});
for(const [name,change] of [
 ['mutable tag',v=>v.images.backend='ghcr.io/wearthewo/sap-timesheet-automation/backend:latest'],
 ['foreign registry',v=>v.images.backend=v.images.backend.replace('ghcr.io','ghcrXio')],
 ['foreign repository',v=>v.images.frontend=v.images.frontend.replace('wearthewo','attacker')],
 ['unexpected service',v=>v.images.other=v.images.backend],
 ['missing backup image',v=>delete v.images.backup],
 ['wrong architecture',v=>v.platform='linux/arm64'],
 ['source path injection',v=>v.revision='../main'],
 ['unpatched upstream postgres instead of reviewed helper image',v=>v.images.postgres=`postgres:17-alpine@sha256:${digest}`],
 ['postgres major upgrade',v=>v.images.postgres=`postgres:18-alpine@sha256:${digest}`],
 ['missing postgres image',v=>delete v.images.postgres],
]) test(`release rejects ${name}`,()=>{const v=release();change(v);assert.throws(()=>validateRelease(v));});
const runs=()=>['ci','e2e'].map((name,i)=>({id:i+1,head_sha:sha,path:`.github/workflows/${name}.yml`,status:'completed',conclusion:'success'}));
test('release requires CI and E2E on the same exact revision',()=>{assertVerification(runs(),sha);assert.throws(()=>assertVerification(runs(),'c'.repeat(40)));assert.throws(()=>assertVerification(runs().slice(0,1),sha));});
test('later failed or running verification cannot be hidden by an earlier success',()=>{
 for(const extra of [{status:'completed',conclusion:'failure'},{status:'in_progress',conclusion:null}])assert.throws(()=>assertVerification([...runs(),{...runs()[1],id:99,...extra}],sha));
});
test('missing independent reviewer protection fails closed',()=>{
 for(const e of [{},{protection_rules:[]},{protection_rules:[{type:'required_reviewers',reviewers:[{}],prevent_self_review:false}]}])assert.throws(()=>assertApproval(e));
 assertApproval({protection_rules:[{type:'required_reviewers',reviewers:[{}],prevent_self_review:true}]});
});
const scan=v=>({SchemaVersion:2,ArtifactType:'container_image',Metadata:{OS:{Family:'debian'}},Results:[{Class:'os-pkgs',Type:'debian',Target:'image',Vulnerabilities:v}]});
test('scan accepts complete reports without high/critical findings',()=>assertScan(scan([{Severity:'LOW'}])));
test('scan fails on high/critical findings without suppressing vendor-qualified entries',()=>{
 for(const Severity of ['HIGH','CRITICAL'])assert.throws(()=>assertScan(scan([{Severity}])));
});
test('scan cannot pass on missing or malformed evidence',()=>{
 for(const value of [{},{SchemaVersion:2,Results:[]},{SchemaVersion:2,Results:[{}]},{SchemaVersion:2,Results:[{Type:'debian',Target:'image',Vulnerabilities:'invalid'}]}])assert.throws(()=>assertScan(value));
});

test('required job results reject skipped/missing/failed jobs despite green workflow',()=>{
 const jobs=['backend','frontend','mock-sap','quality'].map(name=>({name,status:'completed',conclusion:'success'}));
 assertJobs(jobs,jobs.map(j=>j.name));
 for(const conclusion of ['skipped','failure','cancelled'])assert.throws(()=>assertJobs([{...jobs[0],conclusion}],['backend']));
 assert.throws(()=>assertJobs([],['business-workflows']));
});
test('image scan requires supported OS coverage and rejects missing severity',()=>{
 for(const change of [v=>delete v.Metadata,v=>v.Metadata.OS.Eosl=true,v=>v.Results[0].Class='lang-pkgs',v=>v.ArtifactType='filesystem',v=>v.Results[0].Vulnerabilities=[{}]]){
  const v=scan([]);change(v);assert.throws(()=>assertScan(v));
 }
});
