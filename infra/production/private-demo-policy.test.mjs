import {test} from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,sign} from 'node:crypto';
import {hash,authorizePrivateDemo,inspectBundle} from './private-demo-policy.mjs';
import {enforceReleasePolicy} from './enforce-release-policy.mjs';
import {assertScan} from './scan-verification.mjs';
import {services} from './release.mjs';
const now=Date.parse('2026-10-26T12:00:00.000Z'),issued='2026-10-26T11:00:00.000Z';
const {privateKey,publicKey}=generateKeyPairSync('ed25519');
const bytes=v=>Buffer.from(JSON.stringify(v));
function fixture(){
 const files={},bundle={schema:1,revision:'a'.repeat(40),platform:'linux/amd64',scanner:'trivy:0.76.0',dbUpdatedAt:issued,images:{}};
 for(const service of services){
  const config=bytes({os:'linux',architecture:'amd64',config:{Labels:{'org.opencontainers.image.revision':bundle.revision}}});
  const manifest=bytes({schemaVersion:2,mediaType:'application/vnd.oci.image.manifest.v1+json',config:{digest:hash(config),size:config.length},layers:[{digest:'sha256:'+'d'.repeat(64)}]});
  files[`scans/${service}.json`]=bytes({SchemaVersion:2,ArtifactType:'container_image',CreatedAt:issued,Metadata:{ImageID:hash(config),OS:{Family:'debian'}},Results:[{Class:'os-pkgs',Type:'debian',Target:'runtime',Vulnerabilities:service==='keycloak'?[{Severity:'HIGH',VulnerabilityID:'CVE-2026-91403',PkgName:'jdk',PkgIdentifier:{PURL:'pkg:rpm/jdk@21'},InstalledVersion:'21'}]:[]}]});
  files[`sboms/${service}.json`]=bytes({bomFormat:'CycloneDX',components:[{name:'jdk'}]});
  bundle.images[service]={manifest:manifest.toString('base64'),config:config.toString('base64'),archiveHash:'sha256:'+'c'.repeat(64),scanHash:hash(files[`scans/${service}.json`]),sbomHash:hash(files[`sboms/${service}.json`])};
 }
 const evidence=bytes({note:'Synthetic test evidence; never a real release approval.'}),h=hash(evidence);
 files[`evidence/${h.slice(7)}.txt`]=evidence;
 const ref={url:'https://vendor.example.test/advisory',sha256:h};
 const read=p=>{assert.ok(files[p],`Missing ${p}`);return files[p];};
 const inspected=inspectBundle(bytes(bundle),read,now);
 const approval={schema:1,id:'11111111-1111-4111-8111-111111111111',environment:'private-demo',bundleHash:inspected.bundleHash,owner:'risk-owner',reviewer:'independent-reviewer',issuedAt:issued,expiresAt:'2026-11-09T11:00:00.000Z',controls:Object.fromEntries(['ingress-allowlist','identity-authorization','synthetic-data','tls-outbound','backups-recovery','expiry-response'].map(k=>[k,ref])),exceptions:inspected.findings.map(finding=>({finding,vendorEvidence:ref,riskAssessment:'Reviewed synthetic test risk, not an approved real risk.',compensatingControls:'Synthetic test controls independently checked.',remediationPlan:'Patch or close ingress before the explicit expiry.'}))};
 const trust={schema:1,environment:'private-demo',reviewers:[{id:approval.reviewer,publicKey:publicKey.export({type:'spki',format:'pem'})}],revokedApprovalIds:[]};
 const f={files,bundle,approval,trust,read};
 f.refresh=()=>{for(const service of services){bundle.images[service].scanHash=hash(files[`scans/${service}.json`]);bundle.images[service].sbomHash=hash(files[`sboms/${service}.json`]);}approval.bundleHash=hash(bytes(bundle));};
 f.envelope=()=>{const payload=bytes(approval);return {keyId:approval.reviewer,payload:payload.toString('base64'),signature:sign(null,payload,privateKey).toString('base64')};};
 f.run=()=>authorizePrivateDemo(bytes(bundle),read,f.envelope(),trust,{now});
 return f;
}
test('independently signed exact-image finding is accepted by publication and host verifier',()=>{
 const f=fixture(),r=f.run(),envelope=f.envelope();
 const release={...r.release,privateDemo:{bundleHash:r.bundleHash,approvalHash:r.approvalHash,approval:envelope}};
 enforceReleasePolicy(release,{mode:'private-demo',trust:f.trust},(digest,p)=>{assert.equal(digest,r.bundleHash);return p==='bundle.json'?bytes(f.bundle):f.read(p);},now);
 assert.throws(()=>assertScan(JSON.parse(f.files['scans/keycloak.json'])),'Public threshold remains strict');
 assert.throws(()=>enforceReleasePolicy(release,{mode:'public-saas'},()=>{},now));
});
for(const [name,mutate] of [
 ['wrong environment',f=>f.approval.environment='production'],
 ['expired at exact boundary',f=>f.approval.expiresAt=new Date(now).toISOString()],
 ['future approval',f=>f.approval.issuedAt='2026-10-26T13:00:00.000Z'],
 ['over fourteen days',f=>f.approval.expiresAt='2026-11-09T11:00:01.000Z'],
 ['invalid calendar date',f=>f.approval.expiresAt='2026-02-30T11:00:00.000Z'],
 ['missing expiry',f=>delete f.approval.expiresAt],
 ['self approval',f=>f.approval.owner=f.approval.reviewer],
 ['revoked approval',f=>f.trust.revokedApprovalIds.push(f.approval.id)],
 ['missing trusted reviewer',f=>f.trust.reviewers=[]],
 ['wrong trust environment',f=>f.trust.environment='public-saas'],
 ['missing exception',f=>f.approval.exceptions=[]],
 ['duplicate exception',f=>f.approval.exceptions.push(f.approval.exceptions[0])],
 ['wrong image digest',f=>f.approval.exceptions[0].finding.imageDigest='sha256:'+'e'.repeat(64)],
 ['wrong package',f=>f.approval.exceptions[0].finding.package='other'],
 ['wrong version',f=>f.approval.exceptions[0].finding.installedVersion='22'],
 ['wrong CVE',f=>f.approval.exceptions[0].finding.cve='CVE-2026-99999'],
 ['wrong package URL',f=>f.approval.exceptions[0].finding.purl='pkg:rpm/other@21'],
 ['wrong scan target',f=>f.approval.exceptions[0].finding.target='other'],
 ['missing vendor evidence',f=>delete f.approval.exceptions[0].vendorEvidence],
 ['insecure evidence URL',f=>f.approval.exceptions[0].vendorEvidence.url='http://example.test'],
 ['empty risk assessment',f=>f.approval.exceptions[0].riskAssessment=''],
 ['missing compensating control',f=>delete f.approval.controls['ingress-allowlist']],
 ['tampered evidence',f=>f.files[Object.keys(f.files).find(p=>p.startsWith('evidence/'))]=bytes('tampered')],
 ['tampered report',f=>f.files['scans/keycloak.json']=bytes({})],
 ['tampered SBOM',f=>f.files['sboms/keycloak.json']=bytes({})],
 ['missing image',f=>{delete f.bundle.images.backup;f.approval.bundleHash=hash(bytes(f.bundle));}],
 ['stale advisory database',f=>{f.bundle.dbUpdatedAt='2026-10-25T11:59:59.000Z';f.refresh();}],
 ['wrong bundle revision',f=>{f.bundle.revision='b'.repeat(40);f.refresh();}],
 ['unknown policy field',f=>f.approval.allowAll=true],
])test(`private policy rejects ${name}`,()=>{const f=fixture();mutate(f);assert.throws(()=>f.run());});
for(const [name,change] of [
 ['extra unapproved HIGH',r=>r.Results[0].Vulnerabilities.push({...r.Results[0].Vulnerabilities[0],VulnerabilityID:'CVE-2026-99999'})],
 ['UNKNOWN severity',r=>r.Results[0].Vulnerabilities[0].Severity='UNKNOWN'],
 ['unknown vulnerability ID',r=>r.Results[0].Vulnerabilities[0].VulnerabilityID='UNCLASSIFIED'],
 ['wrong scanned image',r=>r.Metadata.ImageID='sha256:'+'f'.repeat(64)],
 ['missing OS inventory',r=>r.Results[0].Class='lang-pkgs'],
 ['missing timestamp',r=>delete r.CreatedAt],
 ['stale scan',r=>r.CreatedAt='2026-10-25T11:00:00.000Z'],
 ['future scan',r=>r.CreatedAt='2026-10-26T13:00:00.000Z'],
 ['unsupported OS',r=>r.Metadata.OS.Eosl=true],
 ['missing package identity',r=>delete r.Results[0].Vulnerabilities[0].PkgIdentifier],
])test(`even signed evidence rejects ${name}`,()=>{const f=fixture(),r=JSON.parse(f.files['scans/keycloak.json']);change(r);f.files['scans/keycloak.json']=bytes(r);f.refresh();assert.throws(()=>f.run());});
test('approval payload/signature modification fails cryptographic verification',()=>{
 for(const field of ['payload','signature']){const f=fixture(),e=f.envelope();e[field]=Buffer.from('modified').toString('base64');assert.throws(()=>authorizePrivateDemo(bytes(f.bundle),f.read,e,f.trust,{now}));}
});
test('substituted reviewer key cannot validate an existing signature',()=>{
 const f=fixture();f.trust.reviewers[0].publicKey=generateKeyPairSync('ed25519').publicKey.export({type:'spki',format:'pem'});assert.throws(()=>f.run());
});
test('modified OCI manifest/configuration and wrong platform are rejected',()=>{
 for(const mutation of ['manifest','architecture','config-digest']){
  const f=fixture(),image=f.bundle.images.keycloak;
  if(mutation==='manifest')image.manifest=Buffer.from('{}').toString('base64');
  else {const c=JSON.parse(Buffer.from(image.config,'base64'));c.architecture='arm64';image.config=bytes(c).toString('base64');if(mutation==='architecture'){const m=JSON.parse(Buffer.from(image.manifest,'base64'));m.config={digest:hash(bytes(c)),size:bytes(c).length};image.manifest=bytes(m).toString('base64');}}
  f.refresh();assert.throws(()=>f.run());
 }
});
test('deployment also rejects modified approval binding',()=>{
 const f=fixture(),r=f.run(),release={...r.release,privateDemo:{bundleHash:r.bundleHash,approvalHash:'sha256:'+'f'.repeat(64),approval:f.envelope()}};
 assert.throws(()=>enforceReleasePolicy(release,{mode:'private-demo',trust:f.trust},(d,p)=>p==='bundle.json'?bytes(f.bundle):f.read(p),now));
});
test('deployment rejects missing policy, missing approval, different digest and expired rollback',()=>{
 const f=fixture(),r=f.run(),release={...r.release,privateDemo:{bundleHash:r.bundleHash,approvalHash:r.approvalHash,approval:f.envelope()}},read=(d,p)=>p==='bundle.json'?bytes(f.bundle):f.read(p);
 for(const config of [{},{mode:'private-demo'},{mode:'unknown'}])assert.throws(()=>enforceReleasePolicy(release,config,read,now));
 assert.throws(()=>enforceReleasePolicy(r.release,{mode:'private-demo',trust:f.trust},read,now));
 const modified=structuredClone(release);modified.images.keycloak=modified.images.keycloak.replace(/sha256:.+$/,'sha256:'+'e'.repeat(64));
 assert.throws(()=>enforceReleasePolicy(modified,{mode:'private-demo',trust:f.trust},read,now));
 assert.throws(()=>enforceReleasePolicy(release,{mode:'private-demo',trust:f.trust},read,now+15*86400000));
});
