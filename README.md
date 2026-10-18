# Employee Timesheet & SAP Automation Platform

[![CI](https://github.com/wearthewo/sap-timesheet-automation/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/wearthewo/sap-timesheet-automation/actions/workflows/ci.yml)
[![Full-stack E2E](https://github.com/wearthewo/sap-timesheet-automation/actions/workflows/e2e.yml/badge.svg?branch=main)](https://github.com/wearthewo/sap-timesheet-automation/actions/workflows/e2e.yml)

CI checks pull requests to `main` and pushes to `main`. Full six-service browser verification runs on main pushes, weekly and manually. Badges reflect GitHub results only after the workflows are pushed and run; local verification is separate. See [Phase 8.5 CI verification](docs/phase8-5-ci-verification.md) for commands, diagnostics, synthetic fixtures and required repository settings.

Checkpoint [12A audit and test reliability](docs/phase12-progress.md) replaces shared browser credentials with isolated per-spec fixture manifests. Onboarding creates its own duplicate-address prerequisite; the mobile timesheet test owns a separately seeded submitted month. The suite runs with two parallel workers, no retries, and per-test artifacts.

For a fresh disposable run, use `node infra/ci/stack.mjs prepare`, `config`, `start`, `seed`, then `test`. `diagnostics` captures redacted evidence and `stop` removes only that run's guarded disposable Compose project and volumes. Never use these commands to reset a development database. `CI_RUN_ID` selects an independent ignored `.verification/<run-id>` directory (default `ci`); simultaneous local runs also need different `CI_FRONTEND_PORT`, `CI_BACKEND_PORT`, `CI_KEYCLOAK_PORT`, `CI_POSTGRES_PORT`, `CI_MAILPIT_WEB_PORT` and `CI_MAILPIT_SMTP_PORT`. Credentials and `browser-fixtures.json` must remain ignored. Each mutable suite run requires fresh fixtures; do not rerun it against previously consumed monthly/export records.

The helper passes `BROWSER_FIXTURES_PATH` and an isolated `PLAYWRIGHT_OUTPUT_DIR`. For focused debugging, supply that manifest plus the matching `BROWSER_BASE_URL`/`MAILPIT_API_URL` to `pnpm test:browser <spec>`. The onboarding spec needs only the URLs and can run before seeding. `PLAYWRIGHT_WORKERS` overrides the default of two; local Docker browser execution uses the existing `CI_BROWSER_CONTAINER` option. GitHub uploads `.verification/ci/browser-results/`, never the credential manifest.

Phase 7 adds durable email notifications, scheduled reminders and owner-only notification history. Phase 6 provides FINANCE/ADMIN monthly operational reporting, approved-hours and SAP-exported-hours projections, and CSV/Excel downloads. Phase 5 durable asynchronous SAP export remains in place: export of approved timesheets for ADMIN and FINANCE through a dedicated gateway and a realistic local mock HTTP service. The existing manager workflow is preserved. Employees record decimal hours, submit for review, see rejection reasons, reopen rejected drafts and resubmit. Managers approve or reject only their direct reports, with immutable decision history.

**Exports now run in the existing backend worker with PostgreSQL jobs, bounded retries and reconciliation. No production SAP/email provider, additional channels, payroll or deployment is included.**

## Architecture and stack

Checkpoint 12B adds a role-aware Overview: ADMIN gets organization setup counts/actions; MANAGER gets direct-report approvals and recent decisions; FINANCE gets SAP operational counts and reporting shortcuts. Linked active employees see their current month and recent rejected months. Empty organizations show real zero counts and setup guidance.

Use the desktop sidebar or wrapping mobile navigation; Log out stays available. Open a project/employee name under Administration, then use the assignment shortcuts. Save project changes details; Archive project stops new time entry and preserves history. SAP is explicitly labelled Simulated · Mock SAP. UNKNOWN requires reconciliation, not blind resending. UI event times are labelled Athens; work dates and download contracts remain unchanged. See [checkpoint progress](docs/phase12-progress.md) for verification and remaining work.

Browser → HTTPS proxy → Next.js Node BFF → Spring Boot → PostgreSQL

Local mock now, real SAP adapter later: Spring Boot → SapTimesheetGateway → SAP S/4HANA

See [architecture](docs/architecture.md) for the diagram, authentication boundaries and future modules.

- Java 21, Spring Boot 3.5.16 (stable 3.5 line), Maven Wrapper 3.9.11.
- Spring Web, Security/JWT, Data JPA, Bean Validation, Flyway, PostgreSQL, springdoc 2.8.17.
- Next.js 16.4.1 App Router, React, strict TypeScript, Tailwind, Node 24, pnpm 12.8.1.
- Keycloak 26.8.1 with NextAuth 4.24.15 OIDC; JUnit 5 and Testcontainers PostgreSQL.
- Node server/standalone output, not static export; no Vercel services.

```text
backend/       Spring Boot feature modules, Flyway, integration tests, Maven Wrapper
frontend/      Next.js UI, OIDC session handling, server-side backend calls
mock-sap/      Node 24 HTTP mock, persistent idempotency, contract tests
infra/         Local Keycloak realm import and PostgreSQL initialization
docs/          Architecture and verification notes
compose.yaml   Six-service local stack
.env.example   Configuration template (no secrets)
AGENTS.md      Coding and scope rules
.nvmrc         Node 24
```

## Local startup

Install Docker Desktop/Engine with Compose. Java and Node are not required on the host for Compose.

1. Copy .env.example to .env.
2. Fill DATABASE_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_CLIENT_SECRET, NEXTAUTH_SECRET, SAP_API_TOKEN and INVITATION_SIGNING_KEY with independent random values (e.g. openssl rand -hex 32).
3. Run:
   ```sh
   docker compose config --quiet
   docker compose up --build
   ```
4. Open http://localhost:3000; Keycloak administration is http://localhost:8180/admin.

PostgreSQL data persists in the postgres_data volume. The local keycloak database is created on first volume initialization. Realm imports also run only when the realm does not exist: changing secrets or URLs in .env after initial import requires updating the Keycloak client in its console. Do not delete persistent volumes to apply normal configuration changes.

Local published ports bind to 127.0.0.1. Port variables control host mappings; when changing frontend/Keycloak ports also update the public URLs, issuer and client settings consistently. Compose-internal addresses are set separately from browser-visible URLs.

## Keycloak setup and first user

The import creates realm workforce and confidential client workforce-web:
- Standard authorization code flow with PKCE S256; password grant and service accounts disabled.
- Redirect: http://localhost:3000/api/auth/callback/keycloak.
- Web origin: http://localhost:3000.
- Post-logout redirect: http://localhost:3000/login.
- Access-token audience: workforce-api.
- Default scopes include basic (required for the access-token sub), profile, email and web-origins.
- Issuer: http://localhost:8180/realms/workforce.
- Client secret comes from KEYCLOAK_CLIENT_SECRET through realm environment substitution.

Choose **Create an account** on the Workforce login page. Register in Keycloak, follow the verification email in local Mailpit (http://localhost:8025), then create your organization in Workforce. The backend creates only the organization and your ADMIN membership, atomically. No employee, project or demo data is seeded. No SQL or admin-console operation is needed for this standard new-user flow.

Fresh realm imports enable registration, email verification, password recovery, a 12-character minimum password and brute-force protection. Existing realms are not updated by editing the import JSON. Review `node infra/keycloak/update-registration.mjs --env-file .env`, then explicitly apply the reviewed local settings with `--apply`. This preserves users, passwords and memberships. Back up configuration first and review [the onboarding guide](docs/phase10-registration-onboarding-verification.md). The original development realm/database are not automatically upgraded by this task.

Registration and sign-in use authorization code flow, PKCE and state. Spring requires a trusted boolean `email_verified` claim for organization creation and business access. Internal identity remains keyed exclusively by Keycloak subject; matching emails never merge users.

### Organization administration (Phase 11)

ADMIN members can now create employee profiles before account registration, invite verified users, manage membership roles and status, assign managers, create/archive projects and manage dated project assignments from **Administration**. Invitations work with existing accounts or new Keycloak registrations and reuse the durable notification worker. No SQL or Keycloak admin-console provisioning is needed for ordinary new-organization setup. See [the administration guide](docs/administration.md) and [Phase 11 verification](docs/phase11-administration-verification.md).

V17 preserves historical records and allows an employee to exist without an account. Membership and employment remain separate; the final active ADMIN cannot be suspended, revoked or demoted. New organizations remain empty until administrators populate them through the UI. `INVITATION_SIGNING_KEY` is server-only and required to send invitations; `INVITATION_VALIDITY=PT168H` is the default expiry.

### Controlled operator maintenance

Use a trusted database session, e.g. docker compose exec postgres psql -U workforce -d workforce (adjust username). Look up the exact Keycloak subject from the Keycloak user record. Do not select a user by unverified frontend input or promote based on email alone.

This SQL is for controlled operator maintenance and special fixtures. Prefer the audited administration UI for ordinary employee, membership, invitation, project and assignment changes.

```sql
-- Replace all example values. First confirm the expected subject and identity.
SELECT id, external_subject, email FROM app_users;
-- Create an organization only after verifying its intended ownership.
INSERT INTO organizations(id,slug,name,status)
VALUES ('<organization-uuid>','your-organization','Your organization','ACTIVE');
INSERT INTO organization_memberships(id,organization_id,user_id,role,status)
SELECT gen_random_uuid(), '<organization-uuid>', id, 'ADMIN', 'ACTIVE'
FROM app_users WHERE external_subject = '<verified-keycloak-subject>';

-- For an EXISTING membership, change its organization role explicitly instead:
UPDATE organization_memberships m SET role = 'ADMIN', updated_at = now()
FROM app_users u WHERE u.id = m.user_id
AND m.organization_id = '<organization-uuid>'
AND u.external_subject = '<verified-keycloak-subject>';

INSERT INTO employees (id, organization_id, user_id, employee_number, active)
SELECT gen_random_uuid(), '<organization-uuid>', id, 'E001', true
FROM app_users WHERE external_subject = '<verified-keycloak-subject>';

-- Optional manager, using verified employee UUIDs:
UPDATE employees SET manager_id = '<manager-employee-uuid>', updated_at = now()
WHERE id = '<employee-uuid>' AND organization_id = '<organization-uuid>';
```

Roles are exclusively owned by organization memberships in PostgreSQL. Changing the deprecated app_users.role grants no privileges. No endpoint accepts role changes. MANAGER can review direct reports within the selected organization; FINANCE and ADMIN can operate its SAP exports and Finance reports. Only ADMIN can write its projects and assignments. Employee numbers and project codes are unique within each organization. A person can have different roles and employee profiles in multiple organizations.

Use Swagger UI with a valid Keycloak access token and an ADMIN application user to create projects and assignments. Tokens must include the workforce-api audience; never commit or log them.

## Employee timesheets

Open Timesheets in the authenticated navigation, choose a month, create a draft, and save individual entries. Each save persists progress. Submit after checking totals; pending and approved months are read-only. The lifecycle is DRAFT → PENDING_APPROVAL → APPROVED or REJECTED. Rejected months require explicit reopening to DRAFT before editing/resubmission. Managers use Approvals to review direct reports.

See [timesheet usage, API and concurrency rules](docs/timesheets.md), including version headers, ownership, assignment-date validation, daily limits, and browser-test setup.

See [manager approval rules and UI](docs/approvals.md) for direct-report authorization, decisions/history, rejection/reopen behavior and version checks. Existing SUBMITTED data migrates to PENDING_APPROVAL without losing entries or submission timestamps.

## Environment variables

| Variable | Purpose |
|---|---|
| DATABASE_URL | Backend JDBC PostgreSQL URL; use jdbc:postgresql://... (convert Render connection URI) |
| DATABASE_USERNAME / DATABASE_PASSWORD | Application database credentials |
| KEYCLOAK_ISSUER_URI | Public, exact token issuer, including /realms/workforce |
| KEYCLOAK_JWK_SET_URI | Backend-reachable signing-key endpoint |
| KEYCLOAK_AUDIENCE | Required access-token audience, workforce-api |
| KEYCLOAK_INTERNAL_URL | Frontend server-reachable realm URL for token/userinfo/JWKS |
| KEYCLOAK_CLIENT_ID / KEYCLOAK_CLIENT_SECRET | Confidential frontend OIDC client |
| NEXTAUTH_URL / NEXTAUTH_SECRET | Canonical frontend URL and session encryption secret |
| BACKEND_API_URL | Next.js server-to-backend base URL |
| FRONTEND_URL | Compose public frontend URL and realm redirect origin |
| KEYCLOAK_PUBLIC_URL | Compose public Keycloak hostname URL |
| KEYCLOAK_ADMIN_USERNAME / KEYCLOAK_ADMIN_PASSWORD | Local Keycloak bootstrap account |
| CORS_ALLOWED_ORIGINS | Comma-separated exact origins, local or future Render frontend |
| MAX_DAILY_HOURS | Backend maximum total hours per work date across projects; default 24 |
| SAP_BASE_URL / SAP_API_TOKEN | Server-only HTTP integration destination and credential |
| SAP_CONNECT_TIMEOUT / SAP_RESPONSE_TIMEOUT | Positive backend durations, defaults 2s / 5s |
| PORT | Runtime listening port, respected by both applications |

Root .env is read by Compose. For host development, export backend variables into the shell; copy relevant frontend variables into frontend/.env.local. Never use NEXT_PUBLIC_ for secrets. The browser does not call Spring Boot directly. Next.js attaches a bearer token server-side and disables fetch caching for user data.

## API

| Method | Path | Access |
|---|---|---|
| GET | /api/v1/auth/me | Authenticated; provisions first user |
| GET | /api/v1/auth/organizations | Authenticated; active memberships, empty for unverified identity |
| POST | /api/v1/organizations | Verified email; name/slug only, UUID Idempotency-Key header; own new ADMIN membership |
| GET | /api/v1/projects | Authenticated, paginated catalog |
| GET | /api/v1/projects/{id} | Authenticated |
| GET | /api/v1/employees/me/projects | Authenticated, current assignments |
| POST | /api/v1/projects | ADMIN |
| PUT | /api/v1/projects/{id} | ADMIN |
| POST | /api/v1/project-assignments | ADMIN |
| GET | /api/v1/approvals/pending | MANAGER, direct reports |
| GET | /api/v1/approvals/timesheets/{id} | MANAGER, direct reports |
| POST | /api/v1/approvals/timesheets/{id}/approve | MANAGER, direct reports; version required |
| POST | /api/v1/approvals/timesheets/{id}/reject | MANAGER, direct reports; version and comment required |
| POST | /api/v1/timesheets/{id}/reopen | Owner of REJECTED sheet; version required |

Lists accept page (zero-based) and size (1–100, default 20). Creation POSTs return 201; lifecycle decisions return 200 and SAP enqueue commands return 202. Project creation includes Location. Project write fields: code, name, description (optional), active. Assignment write fields: employeeId, projectId, validFrom, validTo (optional), active. PUT is full replacement of editable project fields.

Errors use timestamp/status/code/message/path. Expected responses include 400 validation/date errors, 401 unauthenticated, 403 forbidden, 404 not found and 409 duplicates/conflicts.

- Health: http://localhost:8080/actuator/health
- OpenAPI: http://localhost:8080/v3/api-docs
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Frontend liveness: http://localhost:3000/api/health

Future TypeScript client generation will consume /v3/api-docs using a pinned OpenAPI generator in CI. The frontend has only minimal presentation types, not a manually maintained duplicate schema.

## Development and verification

Earlier results: [Phase 6 verification](docs/phase6-verification.md). Current results: [Phase 7 verification](docs/phase7-verification.md).

Use a Java 21 version manager and Node 24 (nvm use with the root .nvmrc), or the scoped container procedure in [verification](docs/verification.md). No global replacement is required.

```sh
cd backend
./mvnw test
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

On Windows use .\\mvnw.cmd test. Maven enforces Java 21 and compiler release 21. Tests require Docker and start their own PostgreSQL container, run real Flyway migrations and validate Hibernate mappings. They do not use H2 or require live Keycloak. Security tests use mock JWTs.

```sh
cd frontend
corepack pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm build
pnpm dev
# With the application running:
pnpm test:smoke
```

Use pnpm 12.8.1; corepack must be installed/enabled in your chosen Node environment if used. Alternatively install pnpm inside a disposable Node 24 container. The lockfile is committed. Frontend builds do not require a live backend or secrets.

## Future Render deployment (not performed)

Backend root backend: ensure mvnw is executable (chmod +x mvnw after a Windows-created checkout), build ./mvnw clean package -DskipTests, start java -jar target/workforce-0.1.0.jar. Render can use the supplied Java 21 Dockerfile. PORT controls Spring Boot.

Frontend root frontend: install pnpm install --frozen-lockfile, build pnpm build, start pnpm start. Set Node 24 and pnpm 12.8.1. next start reads PORT; Docker uses standalone server.js with PORT and HOSTNAME=0.0.0.0.

Use Render's private backend/database addresses for server traffic, HTTPS public frontend/Keycloak URLs, and secret environment variables. Supply a production Keycloak deployment separately; local start-dev is not suitable. Set exact issuer, JWKS reachability, audience, redirect/logout URIs and canonical NEXTAUTH_URL. No production URLs are hardcoded.

See [architecture](docs/architecture.md) for future module boundaries. Manager approval is implemented in Phase 3. Phase 5 adds durable SAP processing and Phase 6 adds Finance reporting. Phase 7 adds local Mailpit email and durable notification processing.

## Phase 5: durable SAP export

ADMIN/FINANCE open **SAP exports**, select an approved month and choose **Export to SAP**. The command returns 202 and queues durable work. Status updates automatically while processing. Eligible terminal failures show **Retry export**; uncertain results show **Reconcile export**. Success displays the external reference. Employee/manager views remain read-only.

PostgreSQL stores one export/job per timesheet and immutable attempt history. Workers claim with SKIP LOCKED, recover expired leases through reconciliation, reuse the stable identity and payload snapshot, and apply bounded exponential backoff. No external queue or separate worker deployment is needed.

See [worker design](docs/sap-export-worker.md), [adapter contract](docs/sap-integration.md) and [verification](docs/phase5-verification.md). V10 creates durable jobs and migrates old PENDING exports safely; V11 creates attempt history. V1-V9 are unchanged.

New commands: POST /api/v1/sap/timesheets/{id}/export/retry and POST /api/v1/sap/timesheets/{id}/export/reconcile. GET /api/v1/sap/timesheets/{id}/export/attempts provides paginated history. Existing export POST now returns 202 and is idempotent.

Worker configuration: SAP_EXPORT_WORKER_ENABLED, SAP_EXPORT_BATCH_SIZE, SAP_EXPORT_POLL_INTERVAL, SAP_EXPORT_MAX_ATTEMPTS, SAP_EXPORT_INITIAL_BACKOFF, SAP_EXPORT_MAX_BACKOFF, SAP_EXPORT_PROCESSING_TIMEOUT and SAP_RECONCILIATION_INTERVAL. Defaults and runtime-role options are documented in the worker guide and .env.example. Compose enables the worker inside the backend; API-only execution can disable it.

## Finance reporting (Phase 6)

Open **Reports** as FINANCE or ADMIN, select year/month, and filter employees, projects, timesheet state or SAP state. The operational view includes active employees without a timesheet. Approved-hours detail includes only APPROVED; SAP-exported mode additionally requires SUCCEEDED.

Download CSV for detailed entries or Excel for five sheets: Summary, Employees, Project Totals, Employee Project Hours, and Approved Hour Details. Summary/Employees are operational context; the other sheets follow the selected approved-hours mode. Set REPORT_MAX_DOWNLOAD_ROWS (default 10000) to bound download memory. No V12 migration was required.

See [reporting semantics, APIs and local fixtures](docs/reporting.md) and [verification results](docs/phase6-verification.md).

## Phase 7: email notifications

Approval/rejection and terminal SAP FAILED/UNKNOWN events now create durable notification intent inside the business transaction. A PostgreSQL-backed worker delivers email asynchronously through Spring SMTP, with bounded retries, leases and immutable attempt history. Scheduled employee reminders, manager direct-report digests and prior-month Finance summaries use an explicit business timezone and database deduplication.

Open **Notifications** in the authenticated navigation to see your own email history. Delivery status is not read/unread status. Local mail is captured by Mailpit at http://localhost:8025; Docker Compose now runs six services. No real provider credentials are needed.

See [notification architecture, reminder rules, SMTP configuration and delivery limitations](docs/notifications.md). New Flyway migrations are V12 (notifications) and V13 (attempt history); the dependency added is Spring Boot's managed mail starter. APIs: GET /api/v1/notifications and GET /api/v1/notifications/{id}, both owner-only.

Copy the new mail/notification settings from .env.example when needed. Compose enables evaluation every 15 minutes and delivery every five seconds. Standalone backend runs leave both schedulers disabled unless enabled explicitly. SMTP unavailability does not prevent backend startup or approval commits.

### Exploring the roles locally

Use four separate Keycloak test accounts (EMPLOYEE, MANAGER, FINANCE, ADMIN) to explore every workflow. Start at http://localhost:3000/login; Keycloak returns you to the app. The Keycloak administration account is for identity setup, not an application ADMIN role. Assign application roles through the trusted database onboarding procedure above. Employees need active profiles and date-valid project assignments; review requires a MANAGER profile referenced by the employee's manager_id. Finance/Admin do not require an employee profile just to operate SAP or reports.

Local verification creates role accounts with credentials in the ignored .verification/phase7-users.json file. This is a local test artifact, not committed seed data or a production onboarding mechanism.

## Phase 8: pre-deployment verification and SAP Excel

FINANCE and ADMIN can use **Download SAP Excel** on a SUCCEEDED SAP export detail page. The backend generates an in-memory workbook from the immutable queued payload and successful export metadata. It does not contact SAP or store generated files. Other roles receive 403 at the download API.

Endpoint: `GET /api/v1/sap/timesheets/{timesheetId}/export.xlsx`. The authenticated Next.js BFF forwards this through `/api/sap/timesheets/{timesheetId}/export.xlsx` without exposing access tokens. Missing/incomplete exports produce structured errors rather than an invalid workbook.

See [Phase 8 verification](docs/phase8-verification.md) for measured results and limitations, and [SAP integration](docs/sap-integration.md) for workbook details. Optional isolated recovery verification: `powershell -File infra/dev/verify-phase8-recovery.ps1` after building Compose images. This creates disposable containers and never removes the development Compose volumes. No deployment is performed.

## Phase 9: organizations

Select **Organization** in the authenticated navigation to switch active memberships. A single membership is selected automatically; an identity with no active memberships sees a safe empty state. The same person can hold different roles in different organizations. Business API clients must send `X-Organization-Id`; obtain eligible IDs through `GET /api/v1/auth/organizations`. The BFF handles this server-side and keeps tokens out of browser JavaScript.

Before starting an upgraded backend against populated Phase 8.5 data, review its ownership and explicitly set `LEGACY_TENANT_CONFIRMED=true` only if all records belong to one organization. V15 otherwise stops. Existing records are preserved under the legacy organization, including roles, approvals, snapshots and notification history. Do not use this acknowledgement for a database mixing unrelated organizations. New installations need explicit memberships using the trusted onboarding SQL above.

See [Phase 9 isolation and verification](docs/phase9-multitenancy-verification.md) for migration decisions, API/BFF contracts, worker isolation, current test results and remaining risks. Organization onboarding and administration UI remain out of scope.

## Production infrastructure preparation (12D)

A separate six-service VPS template is available in [the deployment guide](docs/production-deployment.md): Caddy, Next.js, Spring Boot with workers, production-mode Keycloak, PostgreSQL and private Mock SAP. The local/CI Compose stack still includes Mailpit and is unchanged. Runtime file secrets, pinned multiarchitecture images, encrypted backups, empty-target restoration and operational checks are prepared. No cloud resources, production credentials or public deployment were created. See [checkpoint evidence and limitations](docs/phase12-progress.md) and [Phase 13 launch prerequisites](docs/phase13-deployment-checklist.md); 12E remains a separate checkpoint.
