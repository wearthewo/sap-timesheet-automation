import {test} from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,sign} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {hash,inspectBundle} from './private-demo-policy.mjs';
import {authorizePublicProduction} from './public-production-policy.mjs';
import {enforceReleasePolicy} from './enforce-release-policy.mjs';
import {validateRelease,services} from './release.mjs';
import {assertScan} from './scan-verification.mjs';
const now=Date.parse('2026-10-28T12:00:00.000Z'),issued='2026-10-28T11:00:00.000Z',end='2026-11-04T11:00:00.000Z';
const {privateKey,publicKey}=generateKeyPairSync('ed25519');
const bytes=v=>Buffer.from(JSON.stringify(v));
function fixture(severity='HIGH',disposition='RISK_ACCEPTED') {
 const files={},bundle={schema:2,revision:'a'.repeat(40),platform:'linux/amd64',scanner:'trivy:0.76.0',dbUpdatedAt:issued,javaDbUpdatedAt:issued,javaDbNextUpdate:'2026-10-31T11:00:00.000Z',images:{}};
 for(const service of services) {
  const config=bytes({os:'linux',architecture:'amd64',config:{Labels:{'org.opencontainers.image.revision':bundle.revision}}});
  const manifest=bytes({schemaVersion:2,mediaType:'application/vnd.oci.image.manifest.v1+json',config:{digest:hash(config),size:config.length},layers:[{digest:'sha256:'+'d'.repeat(64)}]});
  files[`scans/${service}.json`]=bytes({SchemaVersion:2,ArtifactType:'container_image',CreatedAt:issued,Metadata:{ImageID:hash(config),OS:{Family:'debian'}},Results:[{Class:'os-pkgs',Type:'debian',Target:'runtime',Packages:[{Name:'synthetic',Version:'1'}],Vulnerabilities:service==='proxy'?[{Severity:severity,VulnerabilityID:'CVE-2099-12345',PkgName:'synthetic',PkgIdentifier:{PURL:'pkg:generic/synthetic@1'},InstalledVersion:'1'}]:[]}]});
  files[`sboms/${service}.json`]=bytes({bomFormat:'CycloneDX',components:[{name:'synthetic'}]});
  bundle.images[service]={manifest:manifest.toString('base64'),config:config.toString('base64'),archiveHash:'sha256:'+'c'.repeat(64),scanHash:hash(files[`scans/${service}.json`]),sbomHash:hash(files[`sboms/${service}.json`])};
 }
 const evidence=bytes({note:'Synthetic test evidence only; not an actual security assessment.'});
 const ref={url:'https://vendor.example.test/synthetic',sha256:hash(evidence)};
 files[`evidence/${ref.sha256.slice(7)}.txt`]=evidence;
 const read=p=>{assert.ok(files[p],`Missing ${p}`);return files[p];};
 const inspected=inspectBundle(bytes(bundle),read,now,{publicProduction:true});
 const approval={schema:1,id:'11111111-1111-4111-8111-111111111111',environment:'public-production',bundleHash:inspected.bundleHash,owner:'risk-owner',reviewer:'independent-reviewer',issuedAt:issued,expiresAt:end,
  baselines:[],controls:Object.fromEntries(['public-ingress','identity-authorization','tls-outbound','backups-recovery','expiry-response','runtime-configuration'].map(k=>[k,ref])),
  decisions:inspected.findings.map(finding=>({finding,disposition,exposure:disposition==='NOT_AFFECTED'?'absent':disposition==='REMEDIATED'?'mitigated':'restricted',impact:disposition==='RISK_ACCEPTED'?'limited-availability':'none',unacceptableExposure:false,vendorEvidence:ref,technicalEvidence:ref,assessment:'Synthetic independently reviewed assessment, not production approval.',compensatingControls:'Synthetic controls tested for this fixture only.',remediationPlan:'Replace the synthetic package before the stated deadline.',expiresAt:end,remediationDueAt:end}))};
 const trust={schema:1,environment:'public-production',reviewers:[{id:approval.reviewer,publicKey:publicKey.export({type:'spki',format:'pem'})}],revokedApprovalIds:[]};
 const f={files,bundle,approval,trust,read};
 f.refresh=()=>{for(const s of services){bundle.images[s].scanHash=hash(files[`scans/${s}.json`]);bundle.images[s].sbomHash=hash(files[`sboms/${s}.json`]);}approval.bundleHash=hash(bytes(bundle));};
 f.envelope=()=>{const payload=bytes(approval);return {keyId:approval.reviewer,payload:payload.toString('base64'),signature:sign(null,payload,privateKey).toString('base64')};};
 f.run=()=>authorizePublicProduction(bytes(bundle),read,f.envelope(),trust,{now});
 f.host=(release,t=now)=>enforceReleasePolicy(release,{mode:'public-saas',trust},(digest,p)=>{assert.equal(digest,hash(bytes(bundle)));return p==='bundle.json'?bytes(bundle):p==='approval.json'?bytes(f.envelope()):read(p);},t);
 f.release=()=>{const r=f.run();return {...r.release,publicAssessment:{bundleHash:r.bundleHash,approvalHash:r.approvalHash}};};
 return f;
}
for(const [severity,disposition] of [['HIGH','RISK_ACCEPTED'],['UNKNOWN','NOT_AFFECTED'],['HIGH','REMEDIATED'],['LOW','TRACKED'],['MEDIUM','TRACKED']]) {
 test(`signed synthetic ${severity}/${disposition} accepted identically by publication and host`,()=>{
  const f=fixture(severity,disposition),before=Buffer.from(f.files['scans/proxy.json']);
  f.host(f.release());assert.deepEqual(f.files['scans/proxy.json'],before,'Raw evidence is unchanged');
  if(['HIGH','UNKNOWN'].includes(severity))assert.throws(()=>assertScan(JSON.parse(before)),'Strict raw scanner remains unchanged');
 });
}
for(const [name,change] of [
 ['missing decision',f=>f.approval.decisions=[]],
 ['duplicate decision',f=>f.approval.decisions.push(f.approval.decisions[0])],
 ['wrong image',f=>f.approval.decisions[0].finding.imageDigest='sha256:'+'e'.repeat(64)],
 ['wrong CVE',f=>f.approval.decisions[0].finding.cve='CVE-2099-54321'],
 ['wrong component',f=>f.approval.decisions[0].finding.package='other'],
 ['wrong version',f=>f.approval.decisions[0].finding.installedVersion='2'],
 ['wrong PURL',f=>f.approval.decisions[0].finding.purl='pkg:generic/other@1'],
 ['wrong environment',f=>f.approval.environment='private-demo'],
 ['wrong trust environment',f=>f.trust.environment='private-demo'],
 ['self approval',f=>f.approval.owner=f.approval.reviewer],
 ['untrusted reviewer',f=>f.trust.reviewers=[]],
 ['revocation',f=>f.trust.revokedApprovalIds.push(f.approval.id)],
 ['tampered scan',f=>f.files['scans/proxy.json']=bytes({})],
 ['tampered SBOM',f=>f.files['sboms/proxy.json']=bytes({})],
 ['tampered technical evidence',f=>f.files[`evidence/${f.approval.decisions[0].technicalEvidence.sha256.slice(7)}.txt`]=bytes({note:'Modified evidence with sufficient length'})],
 ['missing vendor evidence',f=>delete f.approval.decisions[0].vendorEvidence],
 ['HTTP evidence',f=>f.approval.decisions[0].vendorEvidence.url='http://vendor.example.test/advisory'],
 ['missing public controls',f=>delete f.approval.controls['public-ingress']],
 ['unacceptable exposure',f=>f.approval.decisions[0].unacceptableExposure=true],
 ['reachable attack path',f=>f.approval.decisions[0].exposure='reachable'],
 ['tenant disclosure',f=>f.approval.decisions[0].impact='tenant-disclosure'],
 ['authentication bypass',f=>f.approval.decisions[0].impact='authentication-bypass'],
 ['memory corruption',f=>f.approval.decisions[0].impact='memory-corruption'],
 ['service exhaustion',f=>f.approval.decisions[0].impact='service-exhaustion'],
 ['missing assessment',f=>f.approval.decisions[0].assessment=''],
 ['missing remediation',f=>delete f.approval.decisions[0].remediationPlan],
 ['expired decision',f=>f.approval.decisions[0].expiresAt=new Date(now).toISOString()],
 ['overdue remediation',f=>f.approval.decisions[0].remediationDueAt=issued],
 ['future approval',f=>f.approval.issuedAt='2026-10-28T13:00:00.000Z'],
 ['expired approval',f=>f.approval.expiresAt=new Date(now).toISOString()],
 ['invalid date',f=>f.approval.expiresAt='2026-11-31T11:00:00.000Z'],
 ['over thirty days',f=>f.approval.expiresAt='2026-11-28T11:00:00.000Z'],
 ['over fourteen day acceptance',f=>{f.approval.expiresAt='2026-11-12T11:00:00.000Z';f.approval.decisions[0].expiresAt=f.approval.expiresAt;}],
 ['extra policy field',f=>f.approval.allowUnassessed=true],
])test(`public assessment rejects ${name}`,()=>{const f=fixture();change(f);assert.throws(()=>f.run());});

test('UNKNOWN and CRITICAL cannot receive residual-risk acceptance',()=>{
 for(const severity of ['UNKNOWN','CRITICAL'])assert.throws(()=>fixture(severity).run());
});
test('lower severity cannot bypass missing assessment or unacceptable exposure',()=>{
 for(const severity of ['LOW','MEDIUM']){
  const f=fixture(severity,'TRACKED');f.approval.decisions=[];assert.throws(()=>f.run());
  const g=fixture(severity,'TRACKED');g.approval.decisions[0].impact='code-execution';assert.throws(()=>g.run());
 }
});
test('signed false classification with contradictory exposure fails',()=>{
 for(const disposition of ['NOT_AFFECTED','REMEDIATED']){
  const f=fixture('HIGH',disposition);f.approval.decisions[0].exposure='reachable';assert.throws(()=>f.run());
 }
});
test('new findings, missing inventories and stale/modified bundle fail even when re-signed',()=>{
 for(const mutate of [r=>r.Results[0].Vulnerabilities.push({...r.Results[0].Vulnerabilities[0],Severity:'LOW',VulnerabilityID:'CVE-2099-22222'}),r=>delete r.Results[0].Packages,r=>r.Metadata.ImageID='sha256:'+'f'.repeat(64),r=>r.CreatedAt='2026-10-26T11:00:00.000Z']){
  const f=fixture(),r=JSON.parse(f.files['scans/proxy.json']);mutate(r);f.files['scans/proxy.json']=bytes(r);f.refresh();assert.throws(()=>f.run());
 }
 const f=fixture();f.bundle.dbUpdatedAt='2026-10-26T11:00:00.000Z';f.refresh();assert.throws(()=>f.run());
 for(const mutate of [b=>delete b.javaDbUpdatedAt,b=>b.javaDbNextUpdate=issued,b=>b.javaDbUpdatedAt='2026-10-29T11:00:00.000Z',b=>b.javaDbNextUpdate='2026-11-30T11:00:00.000Z']){
  const g=fixture();mutate(g.bundle);g.refresh();assert.throws(()=>g.run());
 }
});
test('invalid signature or substituted key never authorizes public publication',()=>{
 for(const field of ['payload','signature']){
  const f=fixture(),e=f.envelope();e[field]=Buffer.from('tampered').toString('base64');
  assert.throws(()=>authorizePublicProduction(bytes(f.bundle),f.read,e,f.trust,{now}));
 }
 const f=fixture();f.trust.reviewers[0].publicKey=generateKeyPairSync('ed25519').publicKey.export({type:'spki',format:'pem'});assert.throws(()=>f.run());
});
test('host rejects missing assessments, mixed policy, substituted images and expired rollback',()=>{
 const f=fixture(),r=f.release();
 const bare=structuredClone(r);delete bare.publicAssessment;assert.throws(()=>f.host(bare));
 assert.throws(()=>enforceReleasePolicy(r,{mode:'private-demo',trust:f.trust},()=>{},now));
 const mixed={...r,privateDemo:{}};assert.throws(()=>validateRelease(mixed));
 for(const mutate of [v=>v.images.proxy=v.images.proxy.replace(/sha256:.+$/,'sha256:'+'f'.repeat(64)),v=>v.publicAssessment.approvalHash='sha256:'+'f'.repeat(64),v=>v.publicAssessment.bundleHash='sha256:'+'f'.repeat(64)]){
  const v=structuredClone(r);mutate(v);assert.throws(()=>f.host(v));
 }
 assert.throws(()=>f.host(r,Date.parse(end)));
});
test('GitHub publication/deployment and host use the same verifier without rebuilding approved bytes',()=>{
 const source=p=>readFileSync(new URL('../../'+p,import.meta.url),'utf8');
 const publish=source('.github/workflows/production-publish.yml'),deploy=source('.github/workflows/production-deploy.yml');
 assert.match(publish,/public-production-policy\.mjs/);assert.match(deploy,/public-production-policy\.mjs/);
 assert.match(publish,/private-demo-release\.mjs[^\n]+public-saas/);
 assert.match(publish,/--preserve-digests/);assert.match(publish,/private-demo-bundle\.mjs archives/);
 assert.doesNotMatch(publish,/docker build/);assert.match(publish,/PUBLIC_PRODUCTION_TRUST_JSON/);
 assert.match(source('infra/production/enforce-release-policy.mjs'),/authorizePublicProduction/);
 assert.match(source('.github/workflows/private-demo-candidate.yml'),/--list-all-pkgs/);
});

function baselineFixture(severity='MEDIUM') {
 const f=fixture(severity,'TRACKED');
 const {finding,disposition,...review}=f.approval.decisions.pop();
 assert.equal(disposition,'TRACKED');
 f.approval.baselines=[{...review,findings:[finding]}];return f;
}
test('signed exact LOW/MEDIUM baseline requires no individual decision and preserves raw findings',()=>{
 for(const severity of ['LOW','MEDIUM']) {
  const f=baselineFixture(severity),original=Buffer.from(f.files['scans/proxy.json']);
  f.host(f.release());assert.equal(f.approval.decisions.length,0);
  assert.deepEqual(f.files['scans/proxy.json'],original);
 }
});
for(const [name,mutate] of [
 ['empty baseline',f=>f.approval.baselines[0].findings=[]],
 ['wildcard coverage',f=>f.approval.baselines[0].findings='*'],
 ['unacceptable baseline',f=>f.approval.baselines[0].unacceptableExposure=true],
 ['code execution',f=>f.approval.baselines[0].impact='code-execution'],
 ['reachable baseline',f=>f.approval.baselines[0].exposure='reachable'],
 ['expired baseline',f=>f.approval.baselines[0].expiresAt=issued],
 ['missing baseline evidence',f=>delete f.approval.baselines[0].technicalEvidence],
 ['duplicate groups',f=>f.approval.baselines.push(f.approval.baselines[0])],
 ['digest mismatch',f=>f.approval.baselines[0].findings[0].imageDigest='sha256:'+'f'.repeat(64)],
 ['duplicate individual and group',f=>{const {findings,...review}=f.approval.baselines[0];f.approval.decisions.push({...review,finding:findings[0],disposition:'TRACKED'});}],
])test(`baseline fails closed: ${name}`,()=>{const f=baselineFixture();mutate(f);assert.throws(()=>f.run());});
test('severe/UNKNOWN baseline cannot bypass individual assessment',()=>{
 for(const severity of ['HIGH','CRITICAL','UNKNOWN'])assert.throws(()=>baselineFixture(severity).run());
});
