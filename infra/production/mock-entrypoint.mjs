import {readFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';
export function loadMockSecret(env=process.env,read=readFileSync) {
 if(env.SECRET_NAMES!=='SAP_API_TOKEN')throw Error('SAP token file configuration required');
 let value;
 try{value=read('/run/secrets/SAP_API_TOKEN','utf8').replace(/\n+$/,'');}catch{throw Error('SAP token file unavailable');}
 if(value.length<16||/REPLACE|CHANGEME|\0/.test(value))throw Error('Invalid SAP token file');
 env.SAP_API_TOKEN=value;
}
if(process.argv[1]&&pathToFileURL(resolve(process.argv[1])).href===import.meta.url){
 try{process.umask(0o077);loadMockSecret();}catch(error){console.error(error.message);process.exit(1);}
 await import('./mock-runner.mjs');
}
