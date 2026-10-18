// Synthetic production-mode check. Mount private QA files at /qa; never trace auth secrets.
import {chromium,expect as baseExpect} from '@playwright/test';
const expect=baseExpect.configure({timeout:60000});
import {readFileSync,writeFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import assert from 'node:assert/strict';
const secret=n=>readFileSync('/qa/secrets/'+n,'utf8').trim();
const base='https://app.workforce.test',auth='https://auth.workforce.test';
const adminResponse=await fetch('http://keycloak:8080/realms/master/protocol/openid-connect/token',{method:'POST',body:new URLSearchParams({client_id:'admin-cli',grant_type:'password',username:'qa-operator',password:secret('KEYCLOAK_ADMIN_PASSWORD')})});
assert.equal(adminResponse.status,200,'Private admin authentication');
const token=(await adminResponse.json()).access_token;
const username='production-'+randomBytes(6).toString('hex'),password=randomBytes(24).toString('hex');
const created=await fetch('http://keycloak:8080/admin/realms/workforce/users',{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify({username,email:username+'@example.test',emailVerified:true,enabled:true,firstName:'Synthetic',lastName:'Operator',credentials:[{type:'password',value:password,temporary:false}]})});
assert.equal(created.status,201,'Create isolated synthetic identity');
writeFileSync('/qa/browser-identity.json',JSON.stringify({username,password}),{mode:0o600});
const browser=await chromium.launch({headless:true,args:['--disable-dev-shm-usage']});
const context=await browser.newContext({ignoreHTTPSErrors:true,baseURL:base});
const page=await context.newPage();page.setDefaultTimeout(60000);
let checks=0;
try{
 for(const path of ['/admin/','/admin/master/console/','/realms/master/.well-known/openid-configuration','/health/ready']){
  assert.equal((await context.request.get(auth+path)).status(),404);checks++;
 }
 const discovery=await (await context.request.get(auth+'/realms/workforce/.well-known/openid-configuration')).json();
 assert.equal(discovery.issuer,auth+'/realms/workforce');checks++;
 const login=await context.request.get(base+'/login');
 assert.equal(login.status(),200);assert.match(login.headers()['strict-transport-security'],/max-age=31536000/);assert.ok(login.headers()['x-request-id']);checks++;
 const spoof=await context.request.get(base+'/app',{headers:{'X-Forwarded-Host':'evil.example','X-Forwarded-Proto':'http','Forwarded':'host=evil.example;proto=http'}});
 assert.equal(new URL(spoof.url()).origin,base);checks++;
 await page.goto('/login');await page.getByRole('button',{name:'Sign in with your work account'}).click();
 await expect(page).toHaveURL(/^https:\/\/auth\.workforce\.test\//);assert.equal(new URL(page.url()).origin,auth);checks++;
 await page.locator('#username').fill(username);await page.locator('#password').fill(password);await page.getByRole('button',{name:'Sign In',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Create your organization'})).toBeVisible();checks++;
 await page.getByLabel('Organization name',{exact:true}).fill('Production restore fixture');
 await page.getByLabel('Organization address',{exact:true}).fill(username);
 await page.getByRole('button',{name:'Create organization',exact:true}).click();
 await expect(page.getByRole('navigation',{name:'Main navigation'})).toBeVisible();checks++;
 const cookies=await context.cookies();
 for(const name of ['__Secure-next-auth.session-token','workforce-organization']){
  const cookie=cookies.find(c=>c.name===name);assert.ok(cookie,name);assert.equal(cookie.secure,true);assert.equal(cookie.httpOnly,true);assert.equal(cookie.sameSite,'Lax');checks++;
 }
 const session=await page.evaluate(()=>fetch('/api/auth/session').then(r=>r.json()));
 assert.equal(session.accessToken,undefined);assert.equal(session.refreshToken,undefined);checks++;
 assert.equal((await context.request.post(base+'/api/logout',{headers:{Origin:'https://evil.example'}})).status(),403);checks++;
 await page.setViewportSize({width:375,height:812});
 await expect(page.getByRole('button',{name:'Log out',exact:true})).toBeVisible();
 const logoutRequest=page.waitForRequest(r=>new URL(r.url()).pathname==='/api/logout'&&r.method()==='POST');
 await page.getByRole('button',{name:'Log out',exact:true}).click();
 assert.equal((await logoutRequest).headers().origin,base,'Same-origin HTML form preserves Origin for CSRF');
 await expect.poll(()=>new URL(page.url()).pathname).not.toBe('/app');
 if(new URL(page.url()).origin===auth) await page.getByRole('button',{name:/log\s*out/i}).click();
 await expect(page).toHaveURL(base+'/login');assert.ok(!(await context.cookies()).some(c=>/session-token|workforce-organization/.test(c.name)));checks++;
 console.log('Verified secure cookies, tenant onboarding and mobile logout');
 await page.getByRole('button',{name:'Sign in with your work account'}).click();
 await expect(page.locator('#username')).toBeVisible();checks++;
 writeFileSync('/qa/browser-result.json',JSON.stringify({checks,passed:true,scope:'HTTPS proxy, OIDC login/callback, organization membership, cookies, CSRF, mobile logout; local CA ignored only by Chromium'}));
 console.log('Production browser checks passed: '+checks);
}catch(error){ console.error('Safe failure location:',new URL(page.url()).origin+new URL(page.url()).pathname);console.error('Page text:',(await page.locator('body').innerText()).slice(0,800));throw error;}finally{await browser.close();}
