import {test} from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,sign} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {hash,inspectBundle,verifySignedApproval} from './private-demo-policy.mjs';
import {assertApproval} from './release-gates.mjs';
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

function soloFixture(severity='HIGH',disposition='REMEDIATED') {
 const f=fixture(severity,disposition);
 Object.assign(f.trust,{schema:2,approvalMode:'solo-maintainer'});
 Object.assign(f.approval,{schema:2,approvalMode:'solo-maintainer',owner:f.approval.reviewer});
 return f;
}
// Construct the manifest without pre-authorizing it, so host rejection tests
// exercise the host verifier directly rather than failing in the publisher.
function unverifiedRelease(f) {
 const r=inspectBundle(bytes(f.bundle),f.read,now,{publicProduction:true});
 return {...r.release,publicAssessment:{bundleHash:r.bundleHash,approvalHash:hash(JSON.stringify(f.envelope()))}};
}
for(const [severity,disposition] of [['HIGH','REMEDIATED'],['UNKNOWN','NOT_AFFECTED'],['UNKNOWN','REMEDIATED'],['CRITICAL','NOT_AFFECTED']]) {
 test(`explicit solo trust authorizes synthetic ${severity}/${disposition} on publisher and host`,()=>{
  const f=soloFixture(severity,disposition),original=Buffer.from(f.files['scans/proxy.json']);
  f.run();f.host(unverifiedRelease(f));assert.deepEqual(f.files['scans/proxy.json'],original);
 });
}
test('solo LOW/MEDIUM baselines retain exact coverage and evidence checks',()=>{
 for(const severity of ['LOW','MEDIUM']) {
  const f=soloFixture(severity,'TRACKED'),{finding,disposition,...review}=f.approval.decisions.pop();
  assert.equal(disposition,'TRACKED');f.approval.baselines=[{...review,findings:[finding]}];
  f.run();f.host(unverifiedRelease(f));
 }
});
for(const [name,mutate] of [
 ['schema 1 trust does not opt in',f=>{f.trust.schema=1;delete f.trust.approvalMode;}],
 ['schema 1 assessment cannot downgrade trust',f=>{f.approval.schema=1;delete f.approval.approvalMode;}],
 ['independent trust does not authorize solo payload',f=>f.trust.approvalMode='independent'],
 ['solo trust does not authorize independent payload',f=>f.approval.approvalMode='independent'],
 ['unknown trust mode',f=>f.trust.approvalMode='automatic'],
 ['missing trust mode',f=>delete f.trust.approvalMode],
 ['unknown trust schema',f=>f.trust.schema=3],
 ['missing assessment mode',f=>delete f.approval.approvalMode],
 ['signer is not owner',f=>f.approval.owner='another-owner'],
 ['untrusted signer',f=>f.trust.reviewers=[]],
 ['duplicate trusted signer',f=>f.trust.reviewers.push(f.trust.reviewers[0])],
 ['revoked owner approval',f=>f.trust.revokedApprovalIds.push(f.approval.id)],
 ['private trust cannot authorize public release',f=>f.trust.environment='private-demo'],
 ['expired approval',f=>f.approval.expiresAt=issued],
 ['expired determination',f=>f.approval.decisions[0].expiresAt=issued],
 ['missing evidence',f=>delete f.files[`evidence/${f.approval.decisions[0].technicalEvidence.sha256.slice(7)}.txt`]],
 ['modified evidence',f=>f.files[`evidence/${f.approval.decisions[0].technicalEvidence.sha256.slice(7)}.txt`]=bytes({modified:'Evidence changed after approval'})],
 ['exact image mismatch',f=>f.approval.decisions[0].finding.imageDigest='sha256:'+'e'.repeat(64)],
 ['unassessed HIGH',f=>f.approval.decisions=[]],
 ['reachable remediation',f=>f.approval.decisions[0].exposure='reachable'],
 ['unresolved memory corruption',f=>f.approval.decisions[0].impact='memory-corruption'],
 ['unresolved service exhaustion',f=>f.approval.decisions[0].impact='service-exhaustion'],
])test(`solo publisher and host fail closed: ${name}`,()=>{
 const f=soloFixture();mutate(f);
 assert.throws(()=>f.run());assert.throws(()=>f.host(unverifiedRelease(f)));
});
test('solo mode rejects every residual-risk waiver and unassessed severe/UNKNOWN findings',()=>{
 for(const severity of ['LOW','MEDIUM','HIGH','CRITICAL','UNKNOWN']) {
  const f=soloFixture(severity,'RISK_ACCEPTED');
  assert.throws(()=>f.run(),/Solo mode/);assert.throws(()=>f.host(unverifiedRelease(f)),/Solo mode/);
 }
 for(const severity of ['HIGH','CRITICAL','UNKNOWN']) {
  const f=soloFixture(severity);f.approval.decisions=[];
  assert.throws(()=>f.run(),/Unassessed/);assert.throws(()=>f.host(unverifiedRelease(f)),/Unassessed/);
 }
});
test('solo signature, signed mode and external key cannot be substituted by candidate artifacts',()=>{
 for(const mutation of ['signature','payload','candidate-key']) {
  const f=soloFixture(),original=f.envelope();
  if(mutation==='signature')original.signature=Buffer.alloc(64).toString('base64');
  if(mutation==='payload')original.payload=bytes({...f.approval,approvalMode:'independent'}).toString('base64');
  if(mutation==='candidate-key') {
   const attacker=generateKeyPairSync('ed25519');
   f.files['trust.json']=bytes({...f.trust,reviewers:[{id:f.approval.reviewer,publicKey:attacker.publicKey.export({type:'spki',format:'pem'})}]});
   original.signature=sign(null,bytes(f.approval),attacker.privateKey).toString('base64');
  }
  f.envelope=()=>original;
  assert.throws(()=>f.run(),/signature/);assert.throws(()=>f.host(unverifiedRelease(f)),/signature/);
 }
});
test('publication acceptance cannot override a host configured with a different mode or key',()=>{
 const f=soloFixture(),release=f.release();
 f.trust.approvalMode='independent';assert.throws(()=>f.host(release),/mode mismatch/);
 f.trust.approvalMode='solo-maintainer';
 f.trust.reviewers[0].publicKey=generateKeyPairSync('ed25519').publicKey.export({type:'spki',format:'pem'});
 assert.throws(()=>f.host(release),/signature/);
});
test('schema 2 independent mode still requires another signer; private mode cannot opt into schema 2',()=>{
 const f=fixture();Object.assign(f.trust,{schema:2,approvalMode:'independent'});
 Object.assign(f.approval,{schema:2,approvalMode:'independent'});f.run();f.host(unverifiedRelease(f));
 f.approval.owner=f.approval.reviewer;assert.throws(()=>f.run(),/Independent reviewer/);
 const s=soloFixture();s.trust.environment='private-demo';s.approval.environment='private-demo';
 assert.throws(()=>verifySignedApproval(s.envelope(),s.trust,'private-demo'),/public-only/);
});
test('solo environment preflight requires external valid trust, public environment, main and manual dispatch',()=>{
 const f=soloFixture(),environment={name:'production-publish',protection_rules:[]};
 const context={trust:f.trust,eventName:'workflow_dispatch',ref:'refs/heads/main'};
 assertApproval(environment,context);assertApproval({...environment,name:'production'},context);
 for(const change of [c=>delete c.trust,c=>c.eventName='push',c=>c.eventName='pull_request_target',c=>c.ref='refs/heads/feature',c=>c.trust.approvalMode='independent',c=>c.trust.reviewers=[],c=>c.trust.environment='private-demo']) {
  const c=structuredClone(context);change(c);assert.throws(()=>assertApproval(environment,c));
 }
 for(const name of [undefined,'private-demo','private-demo-publish'])assert.throws(()=>assertApproval({...environment,name},context));
});
test('all public workflows use external environment trust; no policy selector is accepted from candidate inputs',()=>{
 const source=p=>readFileSync(new URL('../../.github/workflows/'+p,import.meta.url),'utf8');
 for(const name of ['private-demo-candidate.yml','production-publish.yml','production-deploy.yml']) {
  const workflow=source(name);
  assert.match(workflow,/PUBLIC_PRODUCTION_TRUST_JSON: \$\{\{ vars\.PUBLIC_PRODUCTION_TRUST_JSON \}\}/);
  assert.match(workflow,/environment:/);
  assert.doesNotMatch(workflow,/inputs\.(approvalMode|trust|public_trust)/);
 }
});
