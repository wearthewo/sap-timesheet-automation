import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {approvalTrustMode} from './private-demo-policy.mjs';
export function assertVerification(runs,sha) {
 assert.match(sha,/^[a-f0-9]{40}$/);
 for(const path of ['.github/workflows/ci.yml','.github/workflows/e2e.yml']) {
  const latest=runs.filter(r=>r.head_sha===sha && r.path===path).sort((a,b)=>b.id-a.id)[0];
  assert.ok(latest && latest.status==='completed' && latest.conclusion==='success',`Latest ${path} must pass at exact revision ${sha}`);
 }
}
export function assertApproval(environment,{trust,eventName,ref}={}) {
 if(trust&&approvalTrustMode(trust,'public-production')==='solo-maintainer') {
  assert.ok(['production-publish','production'].includes(environment.name),'Solo gate requires an existing public release environment');
  assert.equal(eventName,'workflow_dispatch','Solo release requires manual dispatch');
  assert.equal(ref,'refs/heads/main','Solo release requires main');
  // This is only a source/environment preflight. Publication and deployment
  // still require the owner's exact-bundle signature via the shared verifier.
  return;
 }
 const gate=environment.protection_rules?.find(r=>r.type==='required_reviewers');
 assert.ok(gate?.reviewers?.length && gate.prevent_self_review===true,'Configure required reviewers and prevent self-review; unsupported GitHub plans must use the documented manual operator release');
}
export function assertJobs(jobs,required) {
 for(const name of required){
  const job=jobs.find(j=>j.name===name);
  assert.ok(job?.status==='completed'&&job.conclusion==='success',`Required job ${name} must succeed; skipped/missing jobs do not qualify`);
 }
}
if(process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 const [sha,environment]=process.argv.slice(2),repo=process.env.GITHUB_REPOSITORY;
 assert.equal(repo,'wearthewo/sap-timesheet-automation');
 assert.ok(['production-publish','production','private-demo-publish','private-demo'].includes(environment));
 const api=path=>JSON.parse(execFileSync('gh',['api',`repos/${repo}/${path}`],{encoding:'utf8'}));
 const runs=api(`actions/runs?head_sha=${sha}&per_page=100`).workflow_runs;
 assertVerification(runs,sha);
 for(const [path,required] of [
  ['.github/workflows/ci.yml',['backend','frontend','mock-sap','quality']],
  ['.github/workflows/e2e.yml',['business-workflows']]
 ]){
  const run=runs.filter(r=>r.head_sha===sha&&r.path===path).sort((a,b)=>b.id-a.id)[0];
  assertJobs(api(`actions/runs/${run.id}/jobs?filter=latest&per_page=100`).jobs,required);
 }
 const publicRelease=['production-publish','production'].includes(environment);
 const trust=publicRelease&&process.env.PUBLIC_PRODUCTION_TRUST_JSON?JSON.parse(process.env.PUBLIC_PRODUCTION_TRUST_JSON):undefined;
 assertApproval(api(`environments/${environment}`),{trust,eventName:process.env.GITHUB_EVENT_NAME,ref:process.env.GITHUB_REF});
 console.log('Exact-revision CI/E2E and configured environment approval policy verified; signed release assessment is still required.');
}
