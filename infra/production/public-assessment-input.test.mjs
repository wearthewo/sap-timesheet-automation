import {test} from 'node:test';
import assert from 'node:assert/strict';
import {assessmentUrl,downloadAssessment} from './public-assessment-input.mjs';
import {hash} from './private-demo-policy.mjs';
const url=`https://raw.githubusercontent.com/example/review/${'a'.repeat(40)}/assessments/release.json`;
const content=Buffer.from(JSON.stringify({approval:{keyId:'synthetic',payload:'e30=',signature:'AA=='},evidence:{}}));
test('immutable assessment transport retains exact bytes; signature remains a separate mandatory check',async()=>{
 assessmentUrl(url);
 assessmentUrl(`https://gist.githubusercontent.com/reviewer/abcd/raw/${'b'.repeat(40)}/review.json`);
 const {content:received}=await downloadAssessment(url,hash(content),async(input,options)=>{
  assert.equal(input,url);assert.equal(options.redirect,'error');assert.ok(options.signal);
  return new Response(content);
 });
 assert.deepEqual(received,content);
});
test('assessment transport rejects mutable, credentialed and off-host URLs',()=>{
 for(const bad of [url.replace('a'.repeat(40),'main'),url.replace('https:','http:'),url.replace('raw.githubusercontent.com','attacker.test'),url+'?token=secret',url+'#fragment',url.replace('https://','https://user:password@'),url.replace('.com/','.com:444/'),url.replace('.json','.txt')])assert.throws(()=>assessmentUrl(bad));
});
test('assessment transport rejects changed bytes, failed/redirect responses, oversized and invalid input',async()=>{
 await assert.rejects(downloadAssessment(url,'sha256:'+'0'.repeat(64),async()=>new Response(content)));
 for(const status of [302,404,500])await assert.rejects(downloadAssessment(url,hash(content),async()=>new Response('',{status})));
 const large=Buffer.alloc(2*1024*1024+1);
 await assert.rejects(downloadAssessment(url,hash(large),async()=>new Response(large)));
 for(const value of ['not json',JSON.stringify({approval:{},evidence:{}}),JSON.stringify({approval:{keyId:'a',payload:'b',signature:'c'},evidence:{},allow:true})]){
  await assert.rejects(downloadAssessment(url,hash(value),async()=>new Response(value)));
 }
});
