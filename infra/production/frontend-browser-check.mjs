// Focused real-browser runtime acceptance against isolated synthetic dependencies.
// No traces, credentials, action links or browser storage are written to artifacts.
// FRONTEND_CHECK_DIR must contain ports.json from the isolated compatibility setup.
import {createRequire} from 'node:module';
import {readFileSync, writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {randomBytes} from 'node:crypto';
import assert from 'node:assert/strict';
const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const {chromium, expect: baseExpect} = require('@playwright/test');
// Match the repository Playwright assertion budget; this standalone check does
// not load playwright.config.ts. Navigation uses the normal bounded 30s budget.
const expect = baseExpect.configure({timeout:15000});
const dir = resolve(process.env.FRONTEND_CHECK_DIR ?? '.verification/phase13-runtime-options/compat');
const {proxy, mail: mailPort} = JSON.parse(readFileSync(resolve(dir, 'ports.json')));
for (const port of [proxy, mailPort]) assert.ok(Number.isInteger(port) && port > 1024 && port < 65536);
const base = 'https://app.runtime.test', auth = 'https://auth.runtime.test';
const browser = await chromium.launch({headless:true,
  ...(process.env.FRONTEND_CHECK_BROWSER ? {executablePath:process.env.FRONTEND_CHECK_BROWSER} : {}),
  args:[`--host-resolver-rules=MAP app.runtime.test 127.0.0.1:${proxy}, MAP auth.runtime.test 127.0.0.1:${proxy}`, '--no-proxy-server']});
const contexts = [], results = [], errors = [];
async function page() {
  const context = await browser.newContext({baseURL:base, ignoreHTTPSErrors:true}); contexts.push(context);
  const p = await context.newPage(); p.setDefaultTimeout(30000);
  p.on('pageerror', error => errors.push({page:new URL(p.url()).origin+new URL(p.url()).pathname,
    message:String(error.message).replace(/https?:\/\/\S+/g,'[URL omitted]').replace(/[A-Za-z0-9_-]{30,}/g,'[opaque value omitted]').slice(0,240),
    source:String(error.stack).replace(/https?:\/\/[^\s)]+/g,value=>{try{const u=new URL(value);return u.origin+u.pathname;}catch{return '[source omitted]';}}).slice(0,800)}));
  return p;
}
const pass = name => { results.push({name,status:'PASS'}); console.log('PASS: '+name); };
const account = role => { const username='runtime-'+role+'-'+randomBytes(5).toString('hex'); return {username,email:username+'@example.test',password:randomBytes(24).toString('hex')}; };
async function mail(a, subject) {
  let messages;
  await expect.poll(async () => {
    const r = await fetch(`http://127.0.0.1:${mailPort}/api/v1/search?query=`+encodeURIComponent(`to:${a.email} subject:"${subject}"`));
    assert.equal(r.status,200); messages=(await r.json()).messages; return messages.length;
  },{timeout:45000}).toBe(1);
  const message=await (await fetch(`http://127.0.0.1:${mailPort}/api/v1/message/${messages[0].ID}`)).json();
  assert.deepEqual(message.To.map(t=>t.Address),[a.email]); return message;
}
async function follow(p, text, kind='identity') {
  const link=kind==='identity' ? text.match(/https?:\/\/[^\s<>]+\/login-actions\/[^\s<>]+/)?.[0]
    : text.match(/https?:\/\/[^\s]+\/invitations\/accept#token=[^\s]+/)?.[0];
  assert.ok(link,'Expected synthetic email action link');
  assert.equal(new URL(link.replaceAll('&amp;','&')).origin,kind==='identity'?auth:base);
  try { await p.goto(link.replaceAll('&amp;','&')); } catch { throw Error('Synthetic action navigation failed'); }
}
async function register(p,a) {
  await p.getByRole('button',{name:'Create an account',exact:true}).click();
  await p.waitForURL(/^https:\/\/auth\.runtime\.test\//, {waitUntil:'domcontentloaded', timeout:30000});
  assert.equal(new URL(p.url()).origin, auth);
  await p.locator('#firstName').fill('Zoë'); await p.locator('#lastName').fill('Runtime');
  await p.locator('#email').fill(a.email); await p.locator('#username').fill(a.username);
  await p.getByRole('button',{name:/register/i}).click();
  await follow(p,(await mail(a,'Verify email')).Text);
  await p.locator('#password-new').fill(a.password);await p.locator('#password-confirm').fill(a.password);
  await p.getByRole('button',{name:/submit/i}).click();
}
async function logout(p) {
  await p.getByRole('button',{name:'Log out',exact:true}).click();
  await p.getByRole('button',{name:/logout/i}).click();await expect(p).toHaveURL(base+'/login');
}
try {
  const p=await page(), admin=account('admin');
  await p.goto('/login'); await expect(p.getByRole('button',{name:'Create an account',exact:true})).toBeEnabled();
  assert.equal(await p.evaluate(async()=> (await fetch('/api/health')).status),200);
  await p.goto('/app'); await expect(p).toHaveURL(base+'/login');
  const noJs=await browser.newContext({baseURL:base,ignoreHTTPSErrors:true,javaScriptEnabled:false});contexts.push(noJs);
  const ssr=await noJs.newPage(); await ssr.goto('/login'); await expect(ssr.getByRole('heading')).toBeVisible();
  pass('SSR without browser JavaScript, health, static hydration and anonymous redirect');
  await register(p,admin); await expect(p.getByRole('heading',{name:'Create your organization'})).toBeVisible();
  await p.getByLabel('Organization name',{exact:true}).fill('Runtime compatibility');
  await p.getByLabel('Organization address',{exact:true}).fill(admin.username);
  await p.getByRole('button',{name:'Create organization',exact:true}).click();
  await expect(p.getByRole('navigation',{name:'Main navigation'})).toBeVisible();
  await p.reload(); await expect(p.getByRole('navigation',{name:'Main navigation'})).toBeVisible();
  pass('Keycloak registration, email verification, PKCE callback and authenticated BFF mutation/read');
  const session=await p.evaluate(()=>fetch('/api/auth/session').then(r=>r.json()));
  assert.equal(session.accessToken,undefined); assert.equal(session.refreshToken,undefined);
  for(const name of ['__Secure-next-auth.session-token','workforce-organization']) {
    const cookie=(await p.context().cookies()).find(c=>c.name===name);
    assert.ok(cookie?.secure&&cookie.httpOnly);assert.equal(cookie.sameSite,'Lax');
  }
  pass('Secret-file-backed session works; secure HttpOnly cookies and no browser bearer tokens');
  await p.getByRole('link',{name:'Reports',exact:true}).click();
  await expect(p.getByRole('heading',{name:'Monthly Finance reports'})).toBeVisible();
  for(const format of ['CSV','Excel']) {
    const extension=format==='CSV'?'csv':'xlsx';
    const responsePromise=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/reports/'+extension);
    const pending=p.waitForEvent('download');await p.getByRole('link',{name:'Download '+format,exact:true}).click();const download=await pending;
    const response=await responsePromise;assert.equal(response.status(),200);
    assert.ok(response.headers()['content-type'].includes(format==='CSV'?'text/csv':'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'));
    assert.match(response.headers()['content-disposition'],/attachment;/);
    assert.match(download.suggestedFilename(),format==='CSV'?/^workforce-approved-hours-\d{4}-\d{2}\.csv$/:/^workforce-approved-hours-\d{4}-\d{2}\.xlsx$/);
    assert.equal(await download.failure(),null);const bytes=readFileSync(await download.path());
    if(format==='Excel')assert.equal(bytes.subarray(0,2).toString(),'PK'); else assert.ok(bytes.length>0&&!bytes.toString().includes('<html'));
  }
  pass('Authorized CSV/XLSX downloads through BFF preserve filenames and file contents');
  const employee=account('employee');await p.goto('/app/admin/invitations');
  const form=p.getByRole('form',{name:'Send invitation',exact:true});
  await form.getByLabel('Invitation email').fill(employee.email);await form.getByLabel('Organization role',{exact:true}).selectOption('EMPLOYEE');
  await form.getByRole('checkbox').check();await form.getByRole('button',{name:'Send invitation'}).click();
  await expect(form.getByRole('status')).toHaveText('Invitation queued for delivery.');
  const invite=await mail(employee,'Workforce organization invitation'), recipient=await page();
  await follow(recipient,invite.Text,'invitation');await register(recipient,employee);
  await recipient.getByRole('button',{name:'Accept invitation',exact:true}).click();
  await expect(recipient.getByRole('navigation',{name:'Main navigation'})).toBeVisible();
  assert.equal(await recipient.evaluate(()=>fetch('/api/reports/xlsx?year=2026&month=10').then(r=>r.status)),403);
  pass('Invitation handoff/registration/acceptance works; employee download is forbidden');
  await p.setViewportSize({width:375,height:812});await logout(p);
  assert.ok(!(await p.context().cookies()).some(c=>/session-token|workforce-organization/.test(c.name)));
  await p.getByRole('button',{name:'Sign in with your work account'}).click();
  await p.getByRole('link',{name:/forgot password/i}).click();await p.locator('#username').fill(admin.username);
  await p.getByRole('button',{name:/submit/i}).click();await follow(p,(await mail(admin,'Reset password')).Text);
  admin.password=randomBytes(24).toString('hex');await p.locator('#password-new').fill(admin.password);await p.locator('#password-confirm').fill(admin.password);
  await p.getByRole('button',{name:/submit/i}).click();await expect(p.getByRole('navigation',{name:'Main navigation'})).toBeVisible();
  await logout(p); await p.getByRole('button',{name:'Sign in with your work account'}).click();
  await p.locator('#username').fill(admin.username);await p.locator('#password').fill(admin.password);await p.locator('#kc-login').click();
  await expect(p.getByRole('navigation',{name:'Main navigation'})).toBeVisible();await logout(p);
  assert.deepEqual(errors,[]);pass('Password recovery, new-session login, mobile logout and no uncaught browser errors');
}catch(error){results.push({name:'Current runtime scenario',status:'FAIL'});for(const context of contexts)for(const p of context.pages()){const url=new URL(p.url());console.error('Safe failure location:',url.origin+url.pathname);}console.error('Runtime check failed:',String(error.message).replace(/https?:\/\/\S+/g,'[URL omitted]'));process.exitCode=1;}
finally {for(const context of contexts)await context.close();await browser.close();writeFileSync(resolve(dir,'browser-results.json'),JSON.stringify({results,errors,passed:!process.exitCode},null,2));}
