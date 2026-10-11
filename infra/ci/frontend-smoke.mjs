import { spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { cpSync } from 'node:fs';
import { waitFor } from './support.mjs';
const pnpm = process.platform === 'win32' ? 'pnpm.cmd' : 'pnpm';
const env = { ...process.env, NEXTAUTH_SECRET: randomBytes(32).toString('hex'), NEXTAUTH_URL: 'http://localhost:3000', HOSTNAME: '127.0.0.1', PORT: '3000' };
// Match the production Docker image's standalone layout and own the server process.
cpSync('.next/static', '.next/standalone/.next/static', { recursive: true });
const server = spawn(process.execPath, ['.next/standalone/server.js'],
  { env, stdio: 'inherit', detached: process.platform !== 'win32' });
try {
  await waitFor(async () => (await fetch('http://localhost:3000/api/health', { signal: AbortSignal.timeout(2000) })).ok, 60000);
  const test = spawn(pnpm, ['test:smoke'], { env, stdio: 'inherit' });
  process.exitCode = await new Promise(resolve => test.on('exit', code => resolve(code ?? 1)));
} finally {
  if (server.exitCode === null) {
    if (process.platform === 'win32') server.kill('SIGTERM');
    else process.kill(-server.pid, 'SIGTERM');
  }
}
