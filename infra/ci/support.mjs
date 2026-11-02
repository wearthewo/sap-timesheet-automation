import { resolve } from 'node:path';
import { writeFileSync } from 'node:fs';
export function reserveFile(path, content) {
  writeFileSync(path, content, { flag:'wx', mode:0o600 });
}
export async function waitFor(check, timeoutMs, intervalMs = 1000) {
  const deadline = Date.now() + timeoutMs;
  do {
    try { if (await check()) return; } catch { /* Bounded readiness polling. */ }
    await new Promise(resolve => setTimeout(resolve, intervalMs));
  } while (Date.now() < deadline);
  throw new Error('Readiness deadline exceeded');
}
export function assertProject(name) {
  if (!/^workforce-ci-[a-f0-9]{12}$/.test(name)) throw new Error('Refusing non-CI Compose project');
  return name;
}
export function sqlLiteral(value) { return "'" + String(value).replaceAll("'", "''") + "'"; }
export function redact(text, secrets) {
  for (const secret of secrets.filter(Boolean).sort((a, b) => b.length - a.length)) text = text.split(secret).join('[REDACTED]');
  return text.replace(/Bearer\s+\S+/gi, 'Bearer [REDACTED]')
    .replace(/((?:__Secure-|__Host-)?next-auth\.[\w.-]+=)[^;\s]+/gi, '$1[REDACTED]')
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[JWE REDACTED]')
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[JWT REDACTED]')
    .replace(/([?#&](?:token|code|state|session_state|key|execution|session_code|client_data|tab_id)=)[^&\s"'<>]+/g, '$1[REDACTED]')
    .replace(/(workforce-invitation=)[^;\s]+/gi, '$1[REDACTED]');
}
export const fixtureNames = ['approvals', 'notifications', 'organizations', 'reports', 'sap-worker', 'sap', 'timesheets'];
export function verificationDirectory(root, runId = 'ci') {
  if (!/^[a-z0-9][a-z0-9-]{0,39}$/.test(runId)) throw new Error('Invalid CI_RUN_ID');
  return resolve(root, '.verification', runId);
}
export function fixtureEnvironment(bundle) {
  const values = { ...bundle.fixtures, TEST_PROJECT_ONE: 'PHASE1-DEMO', TEST_PROJECT_TWO: 'PHASE2-DEMO' };
  for (const [role, user] of Object.entries(bundle.users)) {
    const prefix = role === 'EMPLOYEE' ? 'TEST' : `TEST_${role}`;
    values[`${prefix}_USERNAME`] = user.username;
    values[`${prefix}_PASSWORD`] = user.password;
    values[`TEST_${role}_NAME`] = user.name;
  }
  return values;
}
