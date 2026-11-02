import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {runInNewContext} from 'node:vm';
const template=readFileSync(new URL('./keycloak-theme/workforce/login/password-validation.ftl',import.meta.url),'utf8');
test('theme change is exactly a null-safe listener over the pinned vendor template',()=>{
 const original=template.replace('document.getElementById("${field}")?.addEventListener','document.getElementById("${field}").addEventListener');
 assert.equal(createHash('sha256').update(original).digest('hex'),'a7a7714a846e89d205f82e0cf1987c998f9e67988879906cb7ff90234db2aef1');
 assert.notEqual(original,template);
});
test('email-first missing field produces no exception and password field retains handler',()=>{
 const expression=template.match(/document\.getElementById\("\$\{field\}"\)\?\.addEventListener/)[0].replace('${field}','password');
 runInNewContext(expression+'("change", handler)',{document:{getElementById:()=>null},handler:()=>{}});
 const handler=()=>{},calls=[];
 runInNewContext(expression+'("change", handler)',{document:{getElementById:id=>{assert.equal(id,'password');return {addEventListener:(...args)=>calls.push(args)};}},handler});
 assert.equal(calls.length,1);assert.equal(calls[0][0],'change');assert.equal(calls[0][1],handler);
});
