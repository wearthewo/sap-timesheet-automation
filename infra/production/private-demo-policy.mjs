// Separate opt-in policy. The strict public scanner is deliberately unchanged.
import assert from 'node:assert/strict';
import {createHash,createPublicKey,verify} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {services,validateRelease} from './release.mjs';
export const hash=bytes=>'sha256:'+createHash('sha256').update(bytes).digest('hex');
const digest=value=>assert.match(value,/^sha256:[a-f0-9]{64}$/);
const text=value=>assert.ok(typeof value==='string'&&value.trim().length>=20&&value.length<=8000,'Required assessment/evidence text');
const keys=(value,expected)=>assert.deepEqual(Object.keys(value).sort(),expected.split(' ').sort(),'Unexpected/missing policy fields');
const timestamp=value=>{assert.match(value,/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.000Z$/);const t=Date.parse(value);assert.equal(new Date(t).toISOString(),value);return t;};
const url=value=>{const u=new URL(value);assert.equal(u.protocol,'https:');assert.equal(u.username+u.password,'');};
export const findingKey=f=>JSON.stringify([f.service,f.imageDigest,f.target,f.type,f.package,f.purl,f.installedVersion,f.cve,f.severity]);

// read() is injected only for deterministic tests. Production reads fixed paths.
export function inspectBundle(bundleBytes,read,now=Date.now()) {
 const bundle=JSON.parse(bundleBytes);keys(bundle,'schema revision platform dbUpdatedAt scanner images');
 assert.equal(bundle.schema,1);assert.equal(bundle.platform,'linux/amd64');assert.match(bundle.revision,/^[a-f0-9]{40}$/);
 assert.equal(bundle.scanner,'trivy:0.76.0');
 const fresh=t=>{assert.match(t,/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?Z$/);timestamp(t.slice(0,19)+'.000Z');const age=now-Date.parse(t);assert.ok(age>=0&&age<=86400000,'Scan/database evidence older than 24 hours or future dated');};
 fresh(bundle.dbUpdatedAt);
 assert.deepEqual(Object.keys(bundle.images).sort(),[...services].sort());
 const findings=[],images={};
 for(const service of services) {
  const image=bundle.images[service];keys(image,'manifest config archiveHash scanHash sbomHash');
  for(const field of ['archiveHash','scanHash','sbomHash'])digest(image[field]);
  const manifestBytes=Buffer.from(image.manifest,'base64'),configBytes=Buffer.from(image.config,'base64');
  assert.equal(manifestBytes.toString('base64'),image.manifest);assert.equal(configBytes.toString('base64'),image.config);
  const manifest=JSON.parse(manifestBytes),config=JSON.parse(configBytes),imageDigest=hash(manifestBytes);
  assert.equal(manifest.schemaVersion,2);assert.equal(manifest.mediaType,'application/vnd.oci.image.manifest.v1+json');
  assert.equal(manifest.config.digest,hash(configBytes));assert.equal(manifest.config.size,configBytes.length);
  assert.ok(manifest.layers?.length>0);for(const layer of manifest.layers)digest(layer.digest);
  assert.equal(config.os,'linux');assert.equal(config.architecture,'amd64');
  assert.equal(config.config?.Labels?.['org.opencontainers.image.revision'],bundle.revision);
  const scanBytes=read(`scans/${service}.json`),sbomBytes=read(`sboms/${service}.json`);
  assert.equal(hash(scanBytes),image.scanHash,'Modified scanner evidence');assert.equal(hash(sbomBytes),image.sbomHash,'Modified SBOM evidence');
  const sbom=JSON.parse(sbomBytes);assert.equal(sbom.bomFormat,'CycloneDX');assert.ok(sbom.components?.length>0,'Missing package inventory');
  const report=JSON.parse(scanBytes);
  assert.equal(report.SchemaVersion,2);assert.equal(report.ArtifactType,'container_image');
  assert.equal(report.Metadata?.ImageID,hash(configBytes),'Scanner image does not match OCI configuration');
  assert.ok(report.Metadata?.OS?.Family);assert.notEqual(report.Metadata.OS.Eosl,true);
  fresh(report.CreatedAt);
  assert.ok(report.Results?.some(r=>r.Class==='os-pkgs'),'Missing OS scan');
  for(const result of report.Results) {
   assert.ok(result.Type&&result.Target);assert.ok(result.Vulnerabilities===undefined||Array.isArray(result.Vulnerabilities));
   for(const v of result.Vulnerabilities??[]) {
    // UNKNOWN cannot receive an exception, including known IDs with unknown severity.
    assert.ok(['LOW','MEDIUM','HIGH','CRITICAL'].includes(v.Severity),'Unknown vulnerability severity');
    assert.match(v.VulnerabilityID,/^(CVE-\d{4}-\d{4,}|GHSA-[a-z0-9-]+|GO-\d{4}-\d+)$/,'Unknown vulnerability identity');
    for(const field of [v.PkgName,v.InstalledVersion,v.PkgIdentifier?.PURL])assert.ok(typeof field==='string'&&field.length>0,'Missing exact package identity');
    if(['HIGH','CRITICAL'].includes(v.Severity))findings.push({service,imageDigest,target:result.Target,type:result.Type,package:v.PkgName,purl:v.PkgIdentifier.PURL,installedVersion:v.InstalledVersion,cve:v.VulnerabilityID,severity:v.Severity});
   }
  }
  images[service]=`ghcr.io/wearthewo/sap-timesheet-automation/${service}@${imageDigest}`;
 }
 return {bundle,findings,release:validateRelease({schema:1,revision:bundle.revision,platform:bundle.platform,images}),bundleHash:hash(bundleBytes)};
}

export function authorizePrivateDemo(bundleBytes,read,envelope,trust,{now=Date.now(),release}={}) {
 const inspected=inspectBundle(bundleBytes,read,now);
 keys(trust,'schema environment reviewers revokedApprovalIds');assert.equal(trust.schema,1);assert.equal(trust.environment,'private-demo');
 assert.ok(Array.isArray(trust.reviewers)&&Array.isArray(trust.revokedApprovalIds));
 keys(envelope,'keyId payload signature');
 const reviewers=trust.reviewers.filter(k=>k.id===envelope.keyId);assert.equal(reviewers.length,1,'Missing/ambiguous trusted reviewer');
 const reviewer=reviewers[0];keys(reviewer,'id publicKey');
 const key=createPublicKey(reviewer.publicKey);assert.equal(key.asymmetricKeyType,'ed25519');
 const payload=Buffer.from(envelope.payload,'base64'),signature=Buffer.from(envelope.signature,'base64');
 assert.equal(payload.toString('base64'),envelope.payload);assert.equal(signature.toString('base64'),envelope.signature);
 assert.ok(verify(null,payload,key,signature),'Approval signature missing/invalid or modified');
 const approval=JSON.parse(payload);keys(approval,'schema id environment bundleHash owner reviewer issuedAt expiresAt controls exceptions');
 assert.equal(approval.schema,1);assert.match(approval.id,/^[a-f0-9-]{36}$/);assert.ok(!trust.revokedApprovalIds.includes(approval.id),'Revoked approval');
 assert.equal(approval.environment,'private-demo');assert.equal(approval.reviewer,reviewer.id);
 assert.ok(typeof approval.owner==='string'&&approval.owner.length>=3&&approval.owner!==approval.reviewer,'Independent reviewer required');
 assert.equal(approval.bundleHash,inspected.bundleHash,'Approval does not cover this bundle');
 const issued=timestamp(approval.issuedAt),expires=timestamp(approval.expiresAt);
 assert.ok(issued<=now&&expires>now&&expires>issued&&expires-issued<=14*86400000,'Expired, future or over-14-day approval');
 const evidence=e=>{keys(e,'url sha256');url(e.url);digest(e.sha256);const content=read(`evidence/${e.sha256.slice(7)}.txt`);assert.ok(content.length>=20,'Empty evidence');assert.equal(hash(content),e.sha256,'Missing/modified evidence');};
 const required=['ingress-allowlist','identity-authorization','synthetic-data','tls-outbound','backups-recovery','expiry-response'];
 assert.deepEqual(Object.keys(approval.controls).sort(),required.sort());
 for(const control of Object.values(approval.controls))evidence(control);
 assert.ok(Array.isArray(approval.exceptions));const approved=new Set();
 for(const exception of approval.exceptions) {
  keys(exception,'finding vendorEvidence riskAssessment compensatingControls remediationPlan');
  keys(exception.finding,'service imageDigest target type package purl installedVersion cve severity');
  const id=findingKey(exception.finding);assert.ok(!approved.has(id),'Duplicate exception');approved.add(id);
  assert.ok(inspected.findings.some(f=>findingKey(f)===id),'Exception does not match an actual exact-image finding');
  evidence(exception.vendorEvidence);text(exception.riskAssessment);text(exception.compensatingControls);text(exception.remediationPlan);
 }
 for(const finding of inspected.findings)assert.ok(approved.has(findingKey(finding)),'Unapproved vulnerability');
 if(release) {
  validateRelease(release);assert.deepEqual(release.images,inspected.release.images,'Release image digest mismatch');
  assert.equal(release.revision,inspected.release.revision);
  assert.equal(release.privateDemo?.bundleHash,inspected.bundleHash);assert.equal(release.privateDemo?.approvalHash,hash(JSON.stringify(envelope)));
 }
 return {...inspected,approval,approvalHash:hash(JSON.stringify(envelope))};
}

if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 const [dir,approvalPath,trustPath,releasePath]=process.argv.slice(2);
 assert.ok(dir&&approvalPath&&trustPath,'Bundle, signed approval and external trust file required');
 const read=path=>readFileSync(resolve(dir,path));
 const result=authorizePrivateDemo(read('bundle.json'),read,JSON.parse(readFileSync(approvalPath)),JSON.parse(readFileSync(trustPath)),{
  ...(releasePath?{release:JSON.parse(readFileSync(releasePath))}:{})});
 console.log(`Private-demo policy verified: ${result.bundleHash}; expires ${result.approval.expiresAt}`);
}
