// Disposable CI stack only. No local .env, developer fixtures or production credentials.
import { execFileSync, spawn } from 'node:child_process';
import { randomBytes, randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync, existsSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertProject, sqlLiteral as q, redact, waitFor } from './support.mjs';
const root = fileURLToPath(new URL('../../', import.meta.url));
const directory = resolve(root, '.verification/ci');
const stateFile = resolve(directory, 'state.json');
const command = process.argv[2];
const secret = () => randomBytes(32).toString('hex');
let state;
const save = () => writeFileSync(stateFile, JSON.stringify(state), { mode: 0o600 });
function compose(args, input, quiet = false) {
  assertProject(state.project);
  return execFileSync('docker', ['compose', '--project-directory', root, '--env-file', resolve(directory, 'stack.env'),
    '-p', state.project, '-f', resolve(root, 'compose.yaml'), '-f', resolve(root, 'infra/ci/compose.yaml'), ...args],
  { cwd: root, env: { ...process.env, ...state.env }, input, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024,
    stdio: quiet ? ['pipe', 'pipe', 'pipe'] : input !== undefined ? ['pipe', 'inherit', 'inherit'] : 'inherit' });
}
function sql(input, quiet = true) {
  return compose(['exec', '-T', 'postgres', 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-At', '-U', 'workforce', '-d', 'workforce'], input, quiet);
}
async function api(path, options = {}) {
  const response = await fetch(state.env.KEYCLOAK_PUBLIC_URL + path, { ...options, signal: AbortSignal.timeout(30000) });
  if (!response.ok) throw new Error(`Keycloak fixture request failed: HTTP ${response.status}`);
  return response.status === 204 || response.status === 201 ? null : response.json();
}
async function seed() {
  if (state.seeded) throw new Error('Fixtures already seeded; start a fresh isolated run');
  let token;
  // Discovery/health can be ready before the first cold token request completes.
  await waitFor(async () => {
    token = await api('/realms/master/protocol/openid-connect/token', { method: 'POST',
      body: new URLSearchParams({ grant_type: 'password', client_id: 'admin-cli', username: 'admin', password: state.env.KEYCLOAK_ADMIN_PASSWORD }) });
    return Boolean(token.access_token);
  }, 120000, 2000);
  const headers = { Authorization: `Bearer ${token.access_token}`, 'Content-Type': 'application/json' };
  state.users = {};
  const suffix = state.project.slice(-12);
  const organizationId=randomUUID(), secondOrganizationId=randomUUID();
  state.organizationId=organizationId; state.secondOrganizationId=secondOrganizationId;
  let statements = `BEGIN; INSERT INTO organizations(id,slug,name,status) VALUES (${q(organizationId)},'ci-alpha','CI Alpha','ACTIVE'),(${q(secondOrganizationId)},'ci-beta','CI Beta','ACTIVE');\n`;
  for (const role of ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN', 'TENANT']) {
    const username = `ci-${role.toLowerCase()}-${suffix}`, password = secret();
    const name = role === 'EMPLOYEE' ? 'Νίκος André & "Employee"' : role === 'MANAGER' ? 'Μαρία García & "Review"' : `CI ${role}`;
    state.users[role] = { username, password, name }; save();
    if (process.env.GITHUB_ACTIONS) console.log(`::add-mask::${password}`);
    await api('/admin/realms/workforce/users', { method: 'POST', headers, body: JSON.stringify({ username,
      // Keycloak's person-name validator disallows quotes/ampersands. Special-character
      // coverage belongs in the authoritative application display_name seeded below.
      email: `${username}@example.test`, firstName: 'CI', lastName: role, enabled: true, emailVerified: true,
      credentials: [{ type: 'password', value: password, temporary: false }] }) });
    const [user] = await api(`/admin/realms/workforce/users?username=${username}&exact=true`, { headers });
    const id = randomUUID(); state.users[role].subject = user.id;
    statements += `INSERT INTO app_users(id,external_subject,email,display_name,role) VALUES (${q(id)},${q(user.id)},${q(username + '@example.test')},${q(name)},${q("EMPLOYEE")});\n`;
    statements += `INSERT INTO organization_memberships(id,organization_id,user_id,role,status) VALUES (gen_random_uuid(),${q(organizationId)},${q(id)},${q(role==='TENANT'?'EMPLOYEE':role)},'ACTIVE');\n`;
    if(role==='TENANT') statements += `INSERT INTO organization_memberships(id,organization_id,user_id,role,status) VALUES (gen_random_uuid(),${q(secondOrganizationId)},${q(id)},'FINANCE','ACTIVE');\n`;
    if (role === 'EMPLOYEE' || role === 'MANAGER') {
      const employeeId = randomUUID(); state.users[role].employeeId = employeeId;
      statements += `INSERT INTO employees(organization_id,id,user_id,employee_number,active) VALUES (${q(organizationId)},${q(employeeId)},${q(id)},${q('QA3-' + role[0] + '-' + suffix)},true);\n`;
    }
  }
  const employee = q(state.users.EMPLOYEE.employeeId), manager = q(state.users.MANAGER.employeeId);
  statements += `UPDATE employees SET manager_id=${manager} WHERE id=${employee};\n`;
  for (const code of ['PHASE1-DEMO', 'PHASE2-DEMO', 'MOCK-INVALID', 'MOCK-TRANSIENT-503', 'MOCK-TIMEOUT', 'MOCK-RETRY']) {
    const id = q(randomUUID());
    statements += `INSERT INTO projects(organization_id,id,code,name,active) VALUES (${q(organizationId)},${id},${q(code)},${q(code)},true);
      INSERT INTO project_assignments(id,employee_id,project_id,valid_from,active) VALUES (gen_random_uuid(),${employee},${id},'2020-01-01',true);\n`;
  }
  sql(statements + 'COMMIT;');
  statements = 'BEGIN;\n';
  // Observe a real scheduled reminder while the employee has no timesheet. Later historical
  // scenarios may use this calendar month, but must not change the immutable reminder body.
  await waitFor(() => Number(sql(`SELECT count(*) FROM notifications n JOIN app_users u ON u.id=n.recipient_user_id
    WHERE u.external_subject=${q(state.users.EMPLOYEE.subject)} AND n.type='TIMESHEET_REMINDER' AND n.status='SENT';`).trim()) === 1, 60000);
  const reminderPeriod = sql(`SELECT business_year || '-' || lpad(business_month::text,2,'0') FROM notifications n JOIN app_users u ON u.id=n.recipient_user_id
    WHERE u.external_subject=${q(state.users.EMPLOYEE.subject)} AND n.type='TIMESHEET_REMINDER';`).trim();
  state.fixtures = { TEST_ORGANIZATION_A: organizationId, TEST_ORGANIZATION_B: secondOrganizationId };
  for (const [key, code, year, month, hours] of [
    ['TEST_SAP_SUCCESS_ID','PHASE1-DEMO',2027,1,'6.25'], ['TEST_SAP_FAILURE_ID','MOCK-INVALID',2027,2,'4.50'],
    ['TEST_SAP_TRANSIENT_ID','MOCK-TRANSIENT-503',2027,3,'6.25'], ['TEST_SAP_UNKNOWN_ID','MOCK-TIMEOUT',2027,4,'6.25'],
    ['TEST_SAP_RETRY_ID','MOCK-RETRY',2027,5,'6.25'], ['TEST_NOTIFICATION_FAILURE_ID','MOCK-INVALID',2030,1,'6.25'],
    ['TEST_NOTIFICATION_UNKNOWN_ID','MOCK-TIMEOUT',2030,2,'6.25']]) {
    const id = randomUUID(); state.fixtures[key] = id;
    statements += `INSERT INTO timesheets(id,employee_id,year,month,status,total_hours,submitted_at,version)
      VALUES (${q(id)},${employee},${year},${month},'APPROVED',${hours},now(),3);
      INSERT INTO timesheet_entries(id,timesheet_id,project_id,work_date,hours,description)
      SELECT gen_random_uuid(),${q(id)},id,${q(`${year}-${String(month).padStart(2,'0')}-05`)},${hours},'CI synthetic export' FROM projects WHERE code=${q(code)};
      INSERT INTO timesheet_approvals(id,timesheet_id,manager_employee_id,decision,comment,decided_at,created_at,updated_at)
      VALUES (gen_random_uuid(),${q(id)},${manager},'APPROVED','CI synthetic approval',now(),now(),now());\n`;
  }
  state.fixtures.TEST_REMINDER_MONTH = reminderPeriod;
  state.fixtures.TEST_APPROVAL_MONTH = '2026-10'; state.fixtures.TEST_MONTH = '2026-11';
  sql(statements + 'COMMIT;');
  const reportPrefix = `REPORT6-${suffix}`;
  compose(['exec', '-T', 'postgres', 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-v', `report_prefix=${reportPrefix}`, '-v', `organization_id=${organizationId}`, '-U', 'workforce', '-d', 'workforce'],
    readFileSync(resolve(root, 'infra/dev/reporting-fixtures.sql'), 'utf8'), true);
  state.fixtures.TEST_REPORT_PREFIX = reportPrefix; state.seeded = true; save();
  console.log('Synthetic OIDC roles, two organizations, memberships, assignments, SAP scenarios and report fixtures prepared.');
}
async function tests() {
  if (!state.seeded) throw new Error('Seed required');
  const env = { ...process.env, ...state.fixtures, BROWSER_BASE_URL: state.env.FRONTEND_URL, SMOKE_BASE_URL: state.env.FRONTEND_URL,
    MAILPIT_API_URL: `http://localhost:${state.env.MAILPIT_WEB_PORT}`, TEST_PROJECT_ONE: 'PHASE1-DEMO', TEST_PROJECT_TWO: 'PHASE2-DEMO' };
  for (const [role, user] of Object.entries(state.users)) {
    const prefix = role === 'EMPLOYEE' ? 'TEST' : `TEST_${role}`;
    env[`${prefix}_USERNAME`] = user.username; env[`${prefix}_PASSWORD`] = user.password; env[`TEST_${role}_NAME`] = user.name;
  }
  for (const script of ['test:smoke', 'test:browser']) {
    let executable = process.platform === 'win32' ? 'pnpm.cmd' : 'pnpm', args = [script];
    if (process.env.CI_BROWSER_CONTAINER && !process.env.GITHUB_ACTIONS && script === 'test:smoke') {
      executable = process.execPath; args = ['--test',resolve(root,'frontend/tests/smoke.test.mjs')];
    } else if (process.env.CI_BROWSER_CONTAINER && !process.env.GITHUB_ACTIONS) {
      // Optional local Node 24/Chromium runtime; GitHub runs pnpm natively on Ubuntu.
      env.BROWSER_HOST_MAP = '1';
      env.MAILPIT_API_URL = `http://host.docker.internal:${state.env.MAILPIT_WEB_PORT}`;
      env.SMOKE_BASE_URL = state.env.FRONTEND_URL.replace('localhost', 'host.docker.internal');
      mkdirSync(resolve(root,'frontend/test-results'), { recursive: true });
      executable = 'docker';
      args = ['run','--rm','--init','--ipc=host','--mount',`type=bind,source=${resolve(root,'frontend/tests')},target=/work/tests,readonly`,
        '--mount',`type=bind,source=${resolve(root,'frontend/playwright.config.ts')},target=/work/playwright.config.ts,readonly`,
        '--mount',`type=bind,source=${resolve(root,'frontend/test-results')},target=/work/test-results`];
      for (const key of Object.keys(env).filter(key => /^(TEST_|BROWSER_|SMOKE_|MAILPIT_API_URL$|CI$)/.test(key))) args.push('-e',key);
      args.push(process.env.CI_BROWSER_CONTAINER,'pnpm',script);
    }
    const child = spawn(executable, args, { cwd: resolve(root, 'frontend'), env, stdio: 'inherit', shell: executable === 'pnpm.cmd' });
    const code = await new Promise(resolve => { child.on('error', () => resolve(1)); child.on('exit', resolve); });
    if (code !== 0) throw new Error(`${script} failed`);
  }
  const subjects = Object.values(state.users).map(user => q(user.subject)).join(',');
  const scope = `FROM notifications n JOIN app_users u ON u.id=n.recipient_user_id WHERE u.external_subject IN (${subjects})`;
  await waitFor(() => Number(sql(`SELECT count(*) ${scope} AND n.status<>'SENT';`).trim()) === 0, 60000);
  const intentsPath = resolve(directory,'intents.json');
  writeFileSync(intentsPath,sql(`SELECT coalesce(json_agg(n),'[]'::json) ${scope};`), { mode: 0o600 });
  execFileSync(process.execPath, [resolve(root,'infra/dev/verify-phase8-mailpit.mjs'),intentsPath],
    { env: { ...process.env, MAILPIT_API_URL: `http://localhost:${state.env.MAILPIT_WEB_PORT}` }, stdio: 'inherit' });
}
function diagnostics() {
  const folder = resolve(directory, 'diagnostics'); mkdirSync(folder, { recursive: true });
  const secrets = [...Object.entries(state.env).filter(([key]) => /PASSWORD|SECRET|TOKEN/.test(key)).map(([,value]) => value),
    ...Object.values(state.users ?? {}).map(user => user.password)];
  function sanitize(folder) {
    if (!existsSync(folder)) return;
    for (const item of readdirSync(folder, { withFileTypes: true })) {
      const path = resolve(folder,item.name);
      if (item.isDirectory()) sanitize(path);
      else if (/\.(xml|json|md|txt|log)$/.test(item.name)) writeFileSync(path,redact(readFileSync(path,'utf8'),secrets));
    }
  }
  sanitize(resolve(root,'frontend/test-results'));
  for (const [file, args] of [['services.txt',['ps','-a']], ['services.log',['logs','--no-color','--tail','500']]]) {
    try { writeFileSync(resolve(folder,file), redact(compose(args, undefined, true), secrets)); } catch { console.log(`Could not collect ${file}`); }
  }
  try { writeFileSync(resolve(folder,'jobs.txt'), sql(`SELECT status,operation,attempt_count,cycle_attempts,last_error_code,next_attempt_at FROM sap_export_jobs;
    SELECT type,status,attempt_count FROM notifications;`)); } catch { console.log('Job diagnostics unavailable (database/startup may have failed).'); }
}
try {
  if (command === 'prepare') {
    if (existsSync(stateFile)) throw new Error('CI state already exists; stop prior isolated run and remove only its state file before preparing again');
    mkdirSync(directory, { recursive: true });
    const ports = { FRONTEND_PORT: process.env.CI_FRONTEND_PORT ?? '3000', KEYCLOAK_PORT: process.env.CI_KEYCLOAK_PORT ?? '8180',
      BACKEND_PORT: process.env.CI_BACKEND_PORT ?? '8080', POSTGRES_PORT: process.env.CI_POSTGRES_PORT ?? '5432',
      MAILPIT_WEB_PORT: process.env.CI_MAILPIT_WEB_PORT ?? '8025', MAILPIT_SMTP_PORT: process.env.CI_MAILPIT_SMTP_PORT ?? '1025' };
    for (const port of Object.values(ports)) if (!/^\d+$/.test(port) || +port < 1024 || +port > 65535) throw new Error('Invalid CI port');
    const frontend = `http://localhost:${ports.FRONTEND_PORT}`, keycloak = `http://localhost:${ports.KEYCLOAK_PORT}`;
    state = { project: `workforce-ci-${randomBytes(6).toString('hex')}`, env: { ...ports, DATABASE_USERNAME: 'workforce',
      DATABASE_PASSWORD: secret(), KEYCLOAK_ADMIN_USERNAME: 'admin', KEYCLOAK_ADMIN_PASSWORD: secret(), KEYCLOAK_CLIENT_SECRET: secret(),
      NEXTAUTH_SECRET: secret(), SAP_API_TOKEN: secret(), FRONTEND_URL: frontend, KEYCLOAK_PUBLIC_URL: keycloak,
      KEYCLOAK_ISSUER_URI: `${keycloak}/realms/workforce`, APP_PUBLIC_BASE_URL: frontend, CORS_ALLOWED_ORIGINS: frontend,
      SAP_BASE_URL: 'http://mock-sap:8090', SAP_EXPORT_WORKER_ENABLED: 'true',
      MAIL_HOST: 'mailpit', MAIL_PORT: '1025', MAIL_USERNAME: '', MAIL_PASSWORD: '', MAIL_SMTP_AUTH: 'false', MAIL_STARTTLS_ENABLED: 'false',
      MAIL_FROM: 'noreply@ci.example.test', WORKFORCE_BUSINESS_TIMEZONE: 'Europe/Athens', MAX_DAILY_HOURS: '24', REPORT_MAX_DOWNLOAD_ROWS: '10000',
      NOTIFICATION_SCHEDULER_ENABLED: 'true', NOTIFICATION_WORKER_ENABLED: 'true',
      SAP_EXPORT_POLL_INTERVAL: '5s', SAP_EXPORT_INITIAL_BACKOFF: '2s', SAP_EXPORT_MAX_BACKOFF: '5s', SAP_EXPORT_MAX_ATTEMPTS: '4',
      SAP_RECONCILIATION_INTERVAL: '15s', SAP_RESPONSE_TIMEOUT: '2s',
      NOTIFICATION_EVALUATION_INTERVAL: 'PT5S', NOTIFICATION_POLL_INTERVAL: 'PT2S', TIMESHEET_REMINDER_DAY_OF_MONTH: '1', FINANCE_REMINDER_DAY_OF_MONTH: '1' } };
    save(); writeFileSync(resolve(directory,'stack.env'), Object.entries(state.env).map(([k,v]) => `${k}=${v}`).join('\n'), { mode: 0o600 });
    if (process.env.GITHUB_ACTIONS) for (const [key,value] of Object.entries(state.env)) if (/PASSWORD|SECRET|TOKEN/.test(key)) console.log(`::add-mask::${value}`);
    console.log('Isolated CI configuration generated; credentials remain in ignored state files.');
  } else {
    if (!existsSync(stateFile) && ['stop','diagnostics'].includes(command)) process.exit(0);
    state = JSON.parse(readFileSync(stateFile,'utf8')); assertProject(state.project);
    if (command === 'config') compose(['config','--quiet']);
    else if (command === 'start') {
      compose(['up','--build','-d','--wait','--wait-timeout','600']);
      for (const url of [`http://localhost:${state.env.BACKEND_PORT}/actuator/health`, `${state.env.FRONTEND_URL}/api/health`,
        `${state.env.KEYCLOAK_PUBLIC_URL}/realms/workforce/.well-known/openid-configuration`,
        `http://localhost:${state.env.BACKEND_PORT}/v3/api-docs`, `http://localhost:${state.env.BACKEND_PORT}/swagger-ui/index.html`]) {
        await waitFor(async () => (await fetch(url,{signal:AbortSignal.timeout(3000)})).ok,60000);
      }
      console.log('Six services healthy; health, OIDC, OpenAPI and Swagger ready.');
    } else if (command === 'seed') await seed();
    else if (command === 'test') await tests();
    else if (command === 'diagnostics') diagnostics();
    else if (command === 'stop') compose(['down','--volumes','--remove-orphans','--timeout','20']);
    else throw new Error('Unknown CI command');
  }
} catch (error) {
  // Never print child-process error objects (they may include env, stdin or upstream bodies).
  console.error(`CI ${command} failed: ${error instanceof Error && !('status' in error) && !('spawnargs' in error) ? error.message : 'command failed; inspect redacted diagnostics'}`);
  process.exitCode = 1;
}
