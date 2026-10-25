import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
test('Linux runtime files retain LF in a Windows-style Git checkout', () => {
  const files = execFileSync('git', ['ls-files', '-z'], { cwd: root, encoding: 'utf8' })
    .split('\0').filter(name => /(?:\.sh|\.sql|\.ya?ml|\.service|\.timer|\.conf|(?:^|\/)mvnw|(?:^|\/|\.)Dockerfile|(?:^|\/)Caddyfile)$/.test(name));
  assert.ok(files.length > 20, 'Inspect actual tracked runtime inputs');
  const directory = mkdtempSync(join(tmpdir(), 'workforce-lf-'));
  try {
    // Per-command setting only: neither the real index nor global Git config changes.
    execFileSync('git', ['-c', 'core.autocrlf=true', 'checkout-index',
      '--prefix=' + resolve(directory).replaceAll('\\', '/') + '/', '--', ...files], { cwd: root });
    for (const name of files) {
      assert.equal(readFileSync(join(directory, name)).includes(13), false, name + ' contains CR');
    }
  } finally {
    // Only this test-created temporary directory, never a repository or Docker volume.
    rmSync(directory, { recursive: true, force: true });
  }
});

test('new production scripts and systemd templates also declare LF',()=>{
 const files=execFileSync('git',['ls-files','--cached','--others','--exclude-standard','-z','infra/production'],{cwd:root,encoding:'utf8'}).split('\0').filter(n=>/\.(sh|sql|service|timer|conf)$/.test(n));
 for(const file of files){
  assert.equal(readFileSync(join(root,file)).includes(13),false,file+' has CR in working tree');
  assert.match(execFileSync('git',['check-attr','eol','--',file],{cwd:root,encoding:'utf8'}),/: eol: lf/);
 }
});
