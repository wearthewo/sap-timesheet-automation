// Immutable AMD64 deployment inventory. Generated manifests contain no secrets.
import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
export const services=['proxy','backend','frontend','keycloak','mock-sap','backup','postgres'];
const namespace='ghcr.io/wearthewo/sap-timesheet-automation';
export function validateRelease(value) {
 assert.deepEqual(Object.keys(value).sort(),['schema','platform','revision','images',...(value.privateDemo===undefined?[]:['privateDemo']),...(value.publicAssessment===undefined?[]:['publicAssessment'])].sort());
 assert.ok(!(value.privateDemo&&value.publicAssessment),'Mixed release policies');
 assert.equal(value.schema,1); assert.equal(value.platform,'linux/amd64');
 assert.match(value.revision,/^[a-f0-9]{40}$/);
 assert.deepEqual(Object.keys(value.images).sort(),[...services].sort());
 for(const service of services) {
  const parts=value.images[service].split('@');
  assert.equal(parts.length,2); assert.equal(parts[0],`${namespace}/${service}`);
  assert.match(parts[1],/^sha256:[a-f0-9]{64}$/);
 }
 if(value.privateDemo!==undefined){
  assert.deepEqual(Object.keys(value.privateDemo).sort(),['approval','approvalHash','bundleHash']);
  for(const field of ['approvalHash','bundleHash'])assert.match(value.privateDemo[field],/^sha256:[a-f0-9]{64}$/);
  assert.deepEqual(Object.keys(value.privateDemo.approval).sort(),['keyId','payload','signature']);
 }
 if(value.publicAssessment!==undefined){
  assert.deepEqual(Object.keys(value.publicAssessment).sort(),['approvalHash','bundleHash']);
  for(const field of ['approvalHash','bundleHash'])assert.match(value.publicAssessment[field],/^sha256:[a-f0-9]{64}$/);
 }
 return value;
}
export function composeRelease(value) {
 validateRelease(value);
 return {services:Object.fromEntries(Object.entries(value.images).map(([service,image])=>[service,
  {image,platform:value.platform,pull_policy:'never',...(service==='backup'?{profiles:['operations']}:{})}]))};
}
if(process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 const [command,path,output]=process.argv.slice(2);
 const value=validateRelease(JSON.parse(readFileSync(path,'utf8')));
 if(command==='check') console.log(value.revision);
 else if(command==='compose' && output) writeFileSync(output,JSON.stringify(composeRelease(value),null,2)+'\n',{mode:0o600});
 else throw Error('Use release.mjs check MANIFEST or compose MANIFEST OUTPUT');
}
