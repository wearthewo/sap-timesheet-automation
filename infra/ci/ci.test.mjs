import { test } from 'node:test';
import assert from 'node:assert/strict';
import { waitFor, assertProject, sqlLiteral, redact, fixtureNames, fixtureEnvironment, verificationDirectory, reserveFile } from './support.mjs';
import { resolve, dirname } from 'node:path';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import './notification-verification.test.mjs';
test('cleanup rejects developer project and shell/path injection', () => {
  for (const name of ['workforce', '', '../workforce-ci-123', 'workforce-ci-abc;echo']) assert.throws(() => assertProject(name));
  assert.equal(assertProject('workforce-ci-012345abcdef'), 'workforce-ci-012345abcdef');
});
test('SQL fixture strings preserve apostrophes without executable SQL', () => assert.equal(sqlLiteral("O'Brien"), "'O''Brien'"));
test('diagnostics redact generated credentials and bearer tokens', () => {
  assert.equal(redact('secret-token Bearer other-token', ['secret-token']), '[REDACTED] Bearer [REDACTED]');
  assert.equal(redact('/callback?code=abc&state=xyz', []), '/callback?code=[REDACTED]&state=[REDACTED]');
  assert.equal(redact('/login-actions/action-token?key=secret&execution=private', []), '/login-actions/action-token?key=[REDACTED]&execution=[REDACTED]');
  assert.equal(redact('cookie: next-auth.session-token=secret; __Secure-next-auth.session-token.0=chunk', []), 'cookie: next-auth.session-token=[REDACTED]; __Secure-next-auth.session-token.0=[REDACTED]');
  assert.equal(redact('eyJheader..iv.cipher.tag', []), '[JWE REDACTED]');
  assert.equal(redact('/invitations/accept#token=secret', []), '/invitations/accept#token=[REDACTED]');
  assert.equal(redact('workforce-invitation=secret;', []), 'workforce-invitation=[REDACTED];');
});
test('readiness retries errors and succeeds when condition holds', async () => {
  let count = 0;
  await waitFor(() => { if (++count < 3) throw new Error('starting'); return true; }, 5000, 1);
  assert.equal(count, 3);
});
test('readiness fails within a bound', async () => assert.rejects(waitFor(() => false, 5, 1), /deadline/));

test('run state stays inside verification and rejects path traversal', () => {
  const root = resolve('.');
  assert.equal(verificationDirectory(root), resolve(root, '.verification/ci'));
  assert.notEqual(verificationDirectory(root, 'run-a'), verificationDirectory(root, 'run-b'));
  for (const id of ['../ci', '/tmp', 'C:\\temp', '', 'a/b', '..']) assert.throws(() => verificationDirectory(root,id));
});
test('browser spec fixtures cannot share identities or mutate global environment', () => {
  assert.equal(new Set(fixtureNames).size, 7);
  const first = fixtureEnvironment({ fixtures: { TEST_ORGANIZATION_A:'a' }, users: { EMPLOYEE:{username:'a',password:'secret-a',name:'A'} } });
  const second = fixtureEnvironment({ fixtures: { TEST_ORGANIZATION_A:'b' }, users: { EMPLOYEE:{username:'b',password:'secret-b',name:'B'} } });
  assert.equal(first.TEST_USERNAME,'a');assert.equal(second.TEST_USERNAME,'b');
  assert.notEqual(first.TEST_ORGANIZATION_A,second.TEST_ORGANIZATION_A);
  first.TEST_USERNAME='changed';assert.equal(second.TEST_USERNAME,'b');
});
test('concurrent/repeated run reservations cannot overwrite the first owner', () => {
  const dir=mkdtempSync(resolve(tmpdir(),'workforce-ci-test-'));
  try {
    const file=resolve(dir,'owner');reserveFile(file,'first');
    assert.throws(()=>reserveFile(file,'second'),{code:'EEXIST'});
    assert.equal(readFileSync(file,'utf8'),'first');
  } finally {
    assert.equal(dirname(resolve(dir)),resolve(tmpdir()));
    rmSync(dir,{recursive:true});
  }
});
