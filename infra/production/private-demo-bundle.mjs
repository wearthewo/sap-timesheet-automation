// Assemble evidence; never generate approvals or modify scanner reports.
import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,createReadStream} from 'node:fs';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {resolve} from 'node:path';
import {services} from './release.mjs';
import {hash} from './private-demo-policy.mjs';
const [command,dir,revision]=process.argv.slice(2);
const streamHash=async path=>{const h=createHash('sha256');for await(const part of createReadStream(path))h.update(part);return 'sha256:'+h.digest('hex');};
assert.ok(['create','archives'].includes(command));
if(command==='create') {
 assert.match(revision,/^[a-f0-9]{40}$/);
 const metadata=JSON.parse(readFileSync(resolve(dir,'scanner.json')));
 assert.equal(metadata.Version,'0.76.0');
 const bundle={schema:1,revision,platform:'linux/amd64',scanner:'trivy:0.76.0',dbUpdatedAt:new Date(metadata.VulnerabilityDB.UpdatedAt).toISOString().replace(/\.\d{3}Z$/,'.000Z'),images:{}};
 for(const service of services) {
  const path=resolve(dir,`${service}.tar`);
  const image=JSON.parse(execFileSync(process.env.PYTHON??'python3',['infra/production/oci-inventory.py',path],{encoding:'utf8',timeout:300000}));
  bundle.images[service]={...image,archiveHash:await streamHash(path),scanHash:hash(readFileSync(resolve(dir,`scans/${service}.json`))),sbomHash:hash(readFileSync(resolve(dir,`sboms/${service}.json`)))};
 }
 writeFileSync(resolve(dir,'bundle.json'),JSON.stringify(bundle,null,2)+'\n');
 console.log('Review bundle created; no approval or security clearance implied.');
}else {
 const bundle=JSON.parse(readFileSync(resolve(dir,'bundle.json')));
 assert.deepEqual(Object.keys(bundle.images).sort(),[...services].sort());
 for(const service of services)assert.equal(await streamHash(resolve(dir,`${service}.tar`)),bundle.images[service].archiveHash,'Modified OCI archive');
 console.log('All seven OCI archives match reviewed evidence.');
}
