// Host trust/configuration is installed by the operator, never supplied by SSH stdin.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {validateRelease} from './release.mjs';
import {authorizePrivateDemo} from './private-demo-policy.mjs';
export function enforceReleasePolicy(release,config,readBundle,now=Date.now()) {
 validateRelease(release);
 assert.ok(['public-saas','private-demo'].includes(config.mode),'Explicit deployment policy required');
 if(config.mode==='public-saas') {
  assert.equal(release.privateDemo,undefined,'Private exception cannot authorize public deployment');
  return;
 }
 assert.match(release.privateDemo?.bundleHash??'',/^sha256:[a-f0-9]{64}$/,'Private deployment requires reviewed evidence');
 const read=path=>readBundle(release.privateDemo.bundleHash,path);
 authorizePrivateDemo(read('bundle.json'),read,release.privateDemo.approval,config.trust,{now,release});
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 const [releasePath,configPath,evidenceRoot]=process.argv.slice(2);
 assert.ok(releasePath&&configPath&&evidenceRoot);
 enforceReleasePolicy(JSON.parse(readFileSync(releasePath)),JSON.parse(readFileSync(configPath)),
  (digest,path)=>readFileSync(resolve(evidenceRoot,digest.slice(7),path)));
 console.log('Deployment release policy verified.');
}
