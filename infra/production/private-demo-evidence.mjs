// Materialize reviewed, non-secret evidence supplied explicitly by an operator.
import assert from 'node:assert/strict';
import {mkdirSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {hash} from './private-demo-policy.mjs';
const evidence=JSON.parse(process.env.EVIDENCE_JSON??'null');
assert.ok(evidence&&typeof evidence==='object'&&!Array.isArray(evidence));
const directory=resolve(process.argv[2]);mkdirSync(directory,{recursive:true});
for(const [digest,encoded] of Object.entries(evidence)) {
 assert.match(digest,/^sha256:[a-f0-9]{64}$/);
 const content=Buffer.from(encoded,'base64');assert.equal(content.toString('base64'),encoded);
 assert.ok(content.length>0&&content.length<=65536);assert.equal(hash(content),digest);
 writeFileSync(resolve(directory,digest.slice(7)+'.txt'),content,{flag:'wx',mode:0o600});
}
