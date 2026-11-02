import {test} from 'node:test';
import assert from 'node:assert/strict';
import {loadSecrets} from './frontend-entrypoint.mjs';

const names = 'NEXTAUTH_SECRET KEYCLOAK_CLIENT_SECRET';
test('bootstrap loads mounted secrets, preserving spaces and stripping shell-style trailing LF', () => {
  const env = {SECRET_NAMES: names}; const paths = [];
  loadSecrets(env, path => { paths.push(path); return '  synthetic-test-value  \n\n'; });
  assert.deepEqual(paths, ['/run/secrets/NEXTAUTH_SECRET', '/run/secrets/KEYCLOAK_CLIENT_SECRET']);
  assert.equal(env.NEXTAUTH_SECRET, '  synthetic-test-value  ');
  assert.equal(env.KEYCLOAK_CLIENT_SECRET, env.NEXTAUTH_SECRET);
});
test('bootstrap fails closed for missing configuration and invalid names', () => {
  for (const SECRET_NAMES of [undefined, '', 'NEXTAUTH_SECRET', names + ' ../PRIVATE', names + ' BAD-NAME']) {
    assert.throws(() => loadSecrets({SECRET_NAMES}, () => 'synthetic-test-value'));
  }
});
test('missing file errors do not expose filesystem errors or secret contents', () => {
  assert.throws(() => loadSecrets({SECRET_NAMES: names}, () => { throw Error('private details'); }),
    {message: 'Required secret file missing: NEXTAUTH_SECRET'});
});
test('bootstrap rejects short, placeholder and NUL values without exposing them', () => {
  for (const secret of ['', 'short', 'REPLACE-private-test-value', 'CHANGEME-private-test-value', 'private-test-value\0hidden']) {
    assert.throws(() => loadSecrets({SECRET_NAMES: names}, () => secret), error => !error.message.includes('private-test-value'));
  }
});
