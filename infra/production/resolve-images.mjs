// Explicit maintenance operation. Review generated digest changes before building.
import {execFileSync} from 'node:child_process';
import {writeFileSync} from 'node:fs';
const references={node:'node:24.18.0-bookworm-slim',maven:'maven:3.9.12-eclipse-temurin-21',java:'eclipse-temurin:21-jre-jammy',postgres:'postgres:17-alpine',keycloak:'quay.io/keycloak/keycloak:26.8.1',caddy:'caddy:2.11.7-alpine'};
const images={};
for(const [name,reference] of Object.entries(references)){
 const output=execFileSync('docker',['buildx','imagetools','inspect',reference],{encoding:'utf8'});
 const digest=output.match(/^Digest:\s+(sha256:[a-f0-9]{64})/m)?.[1];
 if(!digest||!output.includes('linux/amd64')||!output.includes('linux/arm64'))throw Error('Expected both architectures: '+reference);
 images[name]={reference:reference+'@'+digest,platforms:['linux/amd64','linux/arm64']};
 console.log(name+': manifest supports amd64 and arm64 (not a runtime test)');
}
writeFileSync(new URL('./images.lock.json',import.meta.url),JSON.stringify(images,null,2)+'\n');
