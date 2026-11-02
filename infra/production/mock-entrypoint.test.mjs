import {test} from 'node:test';
import assert from 'node:assert/strict';
import {loadMockSecret} from './mock-entrypoint.mjs';
test('mock runtime loads only its mounted token without changing its value',()=>{const env={SECRET_NAMES:'SAP_API_TOKEN'};loadMockSecret(env,path=>{assert.equal(path,'/run/secrets/SAP_API_TOKEN');return 'synthetic-token-value\n';});assert.equal(env.SAP_API_TOKEN,'synthetic-token-value');});
test('mock runtime fails closed for bad/missing secret configuration and contents',()=>{
 for(const SECRET_NAMES of [undefined,'','SAP_API_TOKEN ../other'])assert.throws(()=>loadMockSecret({SECRET_NAMES},()=>''));
 for(const value of ['','short','CHANGEME-private-value','private-value-secret\0'])assert.throws(()=>loadMockSecret({SECRET_NAMES:'SAP_API_TOKEN'},()=>value),e=>!e.message.includes('private-value'));
 assert.throws(()=>loadMockSecret({SECRET_NAMES:'SAP_API_TOKEN'},()=>{throw Error('private detail');}),{message:'SAP token file unavailable'});
});
