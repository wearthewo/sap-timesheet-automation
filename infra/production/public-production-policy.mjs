// Public assessments reuse the private policy's cryptographic/OCI evidence primitives.
// This module never creates or signs an approval and never edits scanner output.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {inspectBundle,verifySignedApproval,verifyEvidence,hash,keys,text,timestamp,findingKey} from './private-demo-policy.mjs';
import {validateRelease} from './release.mjs';

export function authorizePublicProduction(bundleBytes,read,envelope,trust,{now=Date.now(),release}={}) {
 const inspected=inspectBundle(bundleBytes,read,now,{publicProduction:true});
 const approval=verifySignedApproval(envelope,trust,'public-production');
 keys(approval,'schema id environment bundleHash owner reviewer issuedAt expiresAt controls decisions baselines');
 assert.equal(approval.schema,1);assert.equal(approval.bundleHash,inspected.bundleHash,'Approval does not cover this bundle');
 const issued=timestamp(approval.issuedAt),expires=timestamp(approval.expiresAt);
 assert.ok(issued<=now&&expires>now&&expires>issued&&expires-issued<=30*86400000,'Invalid or over-30-day assessment');
 const required=['public-ingress','identity-authorization','tls-outbound','backups-recovery','expiry-response','runtime-configuration'];
 assert.deepEqual(Object.keys(approval.controls).sort(),required.sort());
 for(const evidence of Object.values(approval.controls))verifyEvidence(evidence,read);
 assert.ok(Array.isArray(approval.decisions)&&Array.isArray(approval.baselines));
 // A signed baseline enumerates exact LOW/MEDIUM findings, never a severity
 // wildcard. It shares one review/evidence/deadline without separate approvals.
 const decisions=[...approval.decisions];
 for(const baseline of approval.baselines) {
  keys(baseline,'findings exposure impact unacceptableExposure vendorEvidence technicalEvidence assessment compensatingControls remediationPlan expiresAt remediationDueAt');
  assert.ok(Array.isArray(baseline.findings)&&baseline.findings.length>0,'Empty baseline');
  const {findings,...review}=baseline;
  for(const finding of findings) {
   assert.ok(['LOW','MEDIUM'].includes(finding.severity),'Baseline cannot cover severe or unknown findings');
   decisions.push({...review,finding,disposition:'TRACKED'});
  }
 }
 const assessed=new Set(),actual=new Set(inspected.findings.map(findingKey));
 for(const decision of decisions) {
  keys(decision,'finding disposition exposure impact unacceptableExposure vendorEvidence technicalEvidence assessment compensatingControls remediationPlan expiresAt remediationDueAt');
  keys(decision.finding,'service imageDigest target type package purl installedVersion cve severity');
  const id=findingKey(decision.finding);
  assert.ok(actual.has(id),'Decision does not match exact image/component/finding');
  assert.ok(!assessed.has(id),'Duplicate assessment');assessed.add(id);
  assert.equal(decision.unacceptableExposure,false,'Unacceptable exposure blocks every severity');
  const end=timestamp(decision.expiresAt),due=timestamp(decision.remediationDueAt);
  assert.ok(end>now&&end>issued&&end<=expires,'Expired decision or exceeds assessment validity');
  assert.ok(due>now&&due<=end,'Overdue or missing remediation/reassessment deadline');
  verifyEvidence(decision.vendorEvidence,read);verifyEvidence(decision.technicalEvidence,read);
  for(const field of ['assessment','compensatingControls','remediationPlan'])text(decision[field]);
  assert.ok(['none','limited-availability','authentication-bypass','tenant-disclosure','code-execution','memory-corruption','service-exhaustion'].includes(decision.impact),'Unknown impact');
  assert.ok(['absent','mitigated','restricted','reachable'].includes(decision.exposure),'Unknown exposure');
  const severity=decision.finding.severity;
  switch(decision.disposition) {
   case 'NOT_AFFECTED':
    assert.equal(decision.exposure,'absent');assert.equal(decision.impact,'none');break;
   case 'REMEDIATED':
    assert.equal(decision.exposure,'mitigated');assert.equal(decision.impact,'none');break;
   case 'RISK_ACCEPTED':
    // Neither ambiguous severity nor applicable CRITICAL is eligible for a waiver.
    assert.ok(['LOW','MEDIUM','HIGH'].includes(severity),'Unknown/critical risk cannot be accepted');
    assert.equal(decision.exposure,'restricted');assert.equal(decision.impact,'limited-availability','Unacceptable public risk');
    assert.ok(end-issued<=14*86400000,'Risk acceptance exceeds fourteen days');break;
   case 'TRACKED':
    assert.ok(['LOW','MEDIUM'].includes(severity),'Severe/unknown finding needs an explicit technical determination or risk acceptance');
    assert.equal(decision.impact,'none','Residual impact requires explicit acceptance');
    assert.ok(['absent','mitigated','restricted'].includes(decision.exposure));break;
   default: throw Error('Unassessed or blocked finding');
  }
 }
 for(const id of actual)assert.ok(assessed.has(id),'Unassessed vulnerability');
 const approvalHash=hash(JSON.stringify(envelope));
 if(release) {
  validateRelease(release);assert.equal(release.privateDemo,undefined,'Private decision cannot authorize public production');
  assert.deepEqual(release.images,inspected.release.images,'Release image digest mismatch');
  assert.equal(release.revision,inspected.release.revision);
  assert.equal(release.publicAssessment?.bundleHash,inspected.bundleHash);
  assert.equal(release.publicAssessment?.approvalHash,approvalHash,'Release assessment mismatch');
 }
 return {...inspected,approval,approvalHash};
}

if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 const [dir,approvalPath,trustPath,releasePath]=process.argv.slice(2);
 assert.ok(dir&&approvalPath&&trustPath);
 const read=path=>readFileSync(resolve(dir,path));
 const result=authorizePublicProduction(read('bundle.json'),read,JSON.parse(readFileSync(approvalPath)),JSON.parse(readFileSync(trustPath)),{
  ...(releasePath?{release:JSON.parse(readFileSync(releasePath))}:{})});
 console.log(`Public assessment verified: ${result.bundleHash}; expires ${result.approval.expiresAt}`);
}
