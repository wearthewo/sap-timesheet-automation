// Read-only registry metadata audit; never pulls, builds or changes containers/volumes.
import {readFileSync,mkdirSync,writeFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
const lock=JSON.parse(readFileSync(new URL('./images.lock.json',import.meta.url)));
const results=[];
for(const [name,image] of Object.entries(lock)) {
 try {
  const index=JSON.parse(execFileSync('docker',['buildx','imagetools','inspect','--raw',image.reference],{encoding:'utf8',timeout:30000,maxBuffer:4e6}));
  const amd64=index.manifests?.find(m=>m.platform?.os==='linux'&&m.platform.architecture==='amd64');
  if(!amd64?.digest?.match(/^sha256:[a-f0-9]{64}$/)) throw Error('No AMD64 child manifest');
  results.push({name,reference:image.reference,amd64Manifest:amd64.digest,status:'PASS'});
  console.log(`PASS ${name}: ${amd64.digest}`);
 } catch {results.push({name,reference:image.reference,status:'UNVERIFIED'});console.error(`UNVERIFIED ${name}: registry inspection failed`);process.exitCode=1;}
}
mkdirSync('.verification/phase13-contabo',{recursive:true});
writeFileSync('.verification/phase13-contabo/image-manifests.json',JSON.stringify({checkedAt:new Date().toISOString(),results},null,2));
