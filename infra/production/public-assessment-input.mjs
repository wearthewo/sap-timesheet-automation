// Transport only: this hash is not approval. The independent Ed25519 signature is
// checked subsequently by the shared publication/host policy verifier.
import assert from 'node:assert/strict';
import {writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {hash,keys} from './private-demo-policy.mjs';
import {materializeEvidence} from './private-demo-evidence.mjs';
export function assessmentUrl(value) {
 const u=new URL(value);assert.equal(u.protocol,'https:');assert.equal(u.username+u.password+u.port+u.search+u.hash,'');
 const commit='[a-f0-9]{40,64}';
 const valid=u.hostname==='raw.githubusercontent.com'?new RegExp(`^/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/${commit}/[A-Za-z0-9_./-]+\\.json$`).test(u.pathname):
  u.hostname==='gist.githubusercontent.com'&&new RegExp(`^/[A-Za-z0-9_-]+/[a-f0-9]+/raw/${commit}/[A-Za-z0-9_.-]+\\.json$`).test(u.pathname);
 assert.ok(valid,'Use an immutable GitHub raw commit URL with non-secret JSON');return u;
}
export async function downloadAssessment(url,expected,fetcher=fetch) {
 assessmentUrl(url);assert.match(expected,/^sha256:[a-f0-9]{64}$/);
 const response=await fetcher(url,{redirect:'error',signal:AbortSignal.timeout(15000)});
 assert.equal(response.status,200,'Assessment download failed');
 const chunks=[];let size=0;
 for await(const chunk of response.body){size+=chunk.length;assert.ok(size<=2*1024*1024,'Assessment exceeds 2 MiB');chunks.push(Buffer.from(chunk));}
 const content=Buffer.concat(chunks);assert.equal(hash(content),expected,'Modified assessment download');
 const value=JSON.parse(content);keys(value,'approval evidence');
 keys(value.approval,'keyId payload signature');return {value,content};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 const [directory,url,expected]=process.argv.slice(2);
 const {value,content}=await downloadAssessment(url,expected);
 materializeEvidence(value.evidence,resolve(directory,'evidence'));
 writeFileSync(resolve(directory,'approval.json'),JSON.stringify(value.approval),{flag:'wx',mode:0o600});
 writeFileSync(resolve(directory,'assessment-input.json'),content,{flag:'wx',mode:0o600});
 console.log('Assessment bytes retained; signature and release policy must still be verified.');
}
