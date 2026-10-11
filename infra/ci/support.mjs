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
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[JWT REDACTED]')
    .replace(/([?&](?:code|state|session_state)=)[^&\s"'<>]+/g, '$1[REDACTED]');
}
