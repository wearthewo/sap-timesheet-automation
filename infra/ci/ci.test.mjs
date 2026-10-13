import { test } from 'node:test';
import assert from 'node:assert/strict';
import { waitFor, assertProject, sqlLiteral, redact } from './support.mjs';
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
});
test('readiness retries errors and succeeds when condition holds', async () => {
  let count = 0;
  await waitFor(() => { if (++count < 3) throw new Error('starting'); return true; }, 5000, 1);
  assert.equal(count, 3);
});
test('readiness fails within a bound', async () => assert.rejects(waitFor(() => false, 5, 1), /deadline/));
