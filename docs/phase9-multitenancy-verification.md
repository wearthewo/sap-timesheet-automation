# Phase 9: organization isolation

Phase 9 is complete for local verification on 2026-10-12. Changes remain uncommitted on feature/phase9-multitenancy. No push, merge, deployment or Phase 10 implementation was performed.

## Schema and migration

V15__organization_ownership.sql is additive; V1–V14 remain unchanged. Organizations have ACTIVE/SUSPENDED lifecycle state. Memberships hold an organization-specific EMPLOYEE, MANAGER, FINANCE or ADMIN role and ACTIVE/SUSPENDED state. Keycloak remains the identity authority. The deprecated app_users.role is retained for historical compatibility and never grants application privileges.

All eleven business tables physically store organization_id: employees, projects, project_assignments, timesheets, timesheet_entries, timesheet_approvals, sap_timesheet_exports, sap_export_jobs, sap_export_attempts, notifications and notification_attempts. Composite foreign keys reject cross-organization managers, projects, entries, decisions, jobs and recipients. Employee numbers, employee/user relationships, project codes and notification deduplication keys are organization-scoped. UUIDs remain globally unique. Organization ownership cannot be changed after insertion. Child insert triggers derive ownership from their parent; root inserts must supply it explicitly.

### Controlled legacy migration

An empty installation migrates without granting anyone membership. A populated pre-tenant database refuses V15 unless the operator sets LEGACY_TENANT_CONFIRMED=true after verifying that all existing records belong to one organization. This is an explicit migration decision, not an inferred relationship between users. If the database contains multiple unrelated organizations, stop and prepare a reviewed ownership mapping before applying V15; this phase does not guess that mapping.

With confirmation, existing users and their existing roles become memberships of UUID 00000000-0000-0000-0000-000000000001 (slug legacy). Existing IDs, user/employee links, timestamps, decisions, jobs, payload snapshots and delivery history are preserved. Constant column backfill avoids updating immutable audit rows. Keep the confirmed Flyway placeholder configuration stable on that installation. No development data was reset or migrated as part of these code changes.

## Request and BFF contract

GET /api/v1/auth/organizations lists only the authenticated identity's active memberships in active organizations. All business requests require X-Organization-Id. The security filter verifies the identity's active membership on each request. Effective roles come from PostgreSQL; JWT role claims, the deprecated global role and browser payloads cannot elevate privileges. Missing context returns 400, inaccessible membership 403, and cross-organization resource IDs return the existing not-found error after authorization.

GET /api/v1/auth/me without organization context provisions identity only and returns no effective role; with context it includes role, organizationId and organizationName. No new user receives automatic legacy membership.

The BFF selects the sole active membership or redirects to /organizations. Multiple memberships require explicit selection. A Server Action verifies the selected membership and stores its ID in an HttpOnly, SameSite=Lax cookie (Secure over HTTPS). Every backend call revalidates selection and forwards X-Organization-Id along with the server-held access token. Spring independently verifies membership. Logout clears the selection. A stale selection returns to the chooser; users without memberships see an empty state and logout. Switching affects other tabs, which should be reloaded before further work.

Root queries scope by organization. Child reads follow a previously authorized root through database-enforced ownership references. There is no custom ThreadLocal tenant state and no PostgreSQL RLS policy: application query authorization plus composite database constraints enforce the boundary. Manager decisions still require active direct-report ownership and prohibit self-review; FINANCE/ADMIN receive no approval override.

## Background isolation

SAP and notification claims carry the persisted organization ID explicitly into virtual-thread processing. Claims remain global bounded SKIP LOCKED operations, restricted to active organizations. Lease recovery, claim-token fencing, immutable snapshots/history and short DB transactions around external I/O remain intact. Result fencing also checks organization ID. Retry/reconciliation never derives a tenant from an HTTP request.

Notification evaluations run per active organization. Recipient selection, manager digests, Finance counts and deduplication are scoped. Delivery claims recheck active membership and relevant role. New email bodies identify the organization; links require the recipient to select that organization in the app. Historical message bodies are not rewritten.

SAP identity remains SAP_TIMESHEET:<globally-unique-timesheet-UUID>. Same employee/project business codes may exist in separate organizations without colliding in application idempotency. The local mock and gateway remain real HTTP integrations. Organization-specific production SAP credentials/company routing are not implemented; they must be designed before production ERP connectivity.

Membership/suspension changes take effect at the next authorization/claim check. They cannot retract a request or SMTP/HTTP operation already in flight. Suspended organizations' pending jobs pause; already claimed operations may finish and persist their fenced result. SMTP acceptance can still be ambiguous and physically duplicate delivery; no exactly-once guarantee is claimed.

## Final verification evidence

| Scenario | Observed result | Evidence |
|---|---|---|
| Complete backend suite | PASS: 326 tests, zero failures/errors/skips | .verification/backend9-final/release-test.log, release-reports |
| Focused isolation and HTTP conflict checks | PASS: 26 tests; subset of the complete suite, not additional tests | .verification/backend9-final/focused.log |
| Two-organization isolation suite | PASS: 23 tests | PhaseNineIsolationTest |
| Suspended-org claiming and guarded legacy history migration | PASS; original snapshot, external reference, message body and immutable attempts preserved | PhaseNineIsolationTest |
| Backend production package | PASS | .verification/backend9-final/release-package.log |
| Frontend frozen install, lint, typecheck, production build | PASS using Node 24; lint/types repeated after final smoke-test addition | .verification/phase9-frontend-check.log, phase9-stack-start.log, phase9-stack-final.log |
| Frontend smoke tests | 19 passed, including anonymous organization selection protection | tests/smoke.test.mjs |
| Mock SAP | 7 passed | mock-sap/server.test.mjs, .verification/phase9-mock-tests.log |
| Full browser + organization switching | 11 passed, including mobile role switching and logout | tests/browser, .verification/phase9-browser.log |
| Mailpit recipient/content comparison | 26 exact matches across all seven notification types | infra/dev/verify-phase8-mailpit.mjs, .verification/phase9-browser.log |
| Six-service Docker stack / health / OpenAPI / Swagger | PASS; all six healthy on isolated ports and volumes, then safely removed | .verification/ci/diagnostics/services.txt, .verification/phase9-stack-final.log |
| CI helper safety tests | 5 passed | infra/ci/ci.test.mjs |
| GitHub-hosted CI for these changes | Not run; changes remain uncommitted | feature/phase9-multitenancy |

Regression testing exposed and fixed a concurrency mapping defect: one of two concurrent entry writes was rolled back by PostgreSQL with SQLSTATE 40P01, but Spring's CannotAcquireLockException fell through to HTTP 500. The existing simultaneous-write regression expects one 201 and one 409, with exactly one persisted entry. The advice now handles the ConcurrencyFailureException family, covering optimistic, deadlock and serialization conflicts with the existing safe 409 response. Three focused MockMvc tests verify actual HTTP exception dispatch. The final full suite passed with the fix. Assertions were not weakened.

Fixture corrections added explicit memberships to the existing tests, an explicit legacy migration acknowledgement to the older schema-upgrade test, and required timestamps to the new legacy SAP history fixture. One earlier SAP fixture run observed QUEUED instead of UNKNOWN; subsequent full runs passed that unchanged HTTP scenario. This isolated observation remains a test reliability item to monitor on GitHub, not a claim that an unproven product defect was fixed. No test was skipped to obtain the final result.

The isolation matrix includes overlapping ADMIN/EMPLOYEE membership for one identity, forged JWT role claims, deprecated global-role changes, unknown users without membership, missing organization headers, unauthenticated access, membership suspension/role changes, organization suspension, scoped projects and own timesheets, cross-tenant assignment/manager/entry database constraints, SAP detail/state/download/history reads and enqueue/retry/reconcile denial, direct-report approval restrictions, scoped Finance summary/CSV, tenant-carrying worker claims/results, concurrent SKIP LOCKED claims, scoped notification histories/recipients/deduplication, suspended job claiming and guarded legacy migration. Existing suites retain lifecycle, self-review, retry, snapshot, download, notification, timezone and reporting assertions.

Backend commands: ./mvnw test and ./mvnw clean package -DskipTests in maven:3.9-eclipse-temurin-21 with the repository Maven Wrapper, Docker socket for Testcontainers, a scoped Maven cache, and copied Linux sources. The verifier sets a 1536 MiB test heap and two active processors. Frontend commands: pnpm install --frozen-lockfile, pnpm lint, pnpm typecheck, pnpm build, pnpm test:smoke in the existing workforce-browser-tests Node 24/pnpm 12.8.1 runtime. Production dependency audit passed. A trailing CR-only line in the temporary verifier caused its shell exit to fail after all these commands succeeded; the temporary script was corrected.

Exact final backend commands (in the copied backend working directory):

```sh
./mvnw -B -ntp test -Dtest=PhaseNineIsolationTest,ExceptionHandlerAdviceTest '-DargLine=-Xmx1536m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1'
./mvnw -B -ntp test '-DargLine=-Xmx1536m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1'
./mvnw -B -ntp clean package -DskipTests
```

The scoped test-context cache setting closes older application contexts instead of retaining pools for already-stopped Testcontainers; it does not skip tests or alter production behavior. The final frontend check repeated frozen install, lint and typecheck after the new smoke test was added. Production build was also verified by the Node 24 Dockerfile. `pnpm audit --prod --audit-level high` reported no known vulnerabilities. CI helper command: `node --test infra/ci/ci.test.mjs`.

Mock command: node --test mock-sap/server.test.mjs with repository-scoped Node 24. Docker execution requires outside-sandbox permission on this Windows host. No global Java/Node installation was changed.

Infrastructure commands ran through `node infra/ci/stack.mjs prepare`, `config` (docker compose config --quiet), `start` (docker compose up --build -d --wait --wait-timeout 600), `seed`, `test` (pnpm test:smoke and pnpm test:browser), `diagnostics` and `stop`. The disposable project was workforce-ci-316268cb454e, with frontend 13000, backend 18080, Keycloak 18180, PostgreSQL 15432 and Mailpit 18025/11025. All fixtures and credentials were synthetic and remained in ignored files. Cleanup removed only that isolated project's containers/volumes; the original workforce development services were not migrated or reset.

The browser suite exercised employee entry mutations/totals/submission, rejection/reopening/resubmission/approval and immutable decision history, Finance SAP enqueue/success/validation failure/transient failure/retry/UNKNOWN reconciliation, SAP snapshot XLSX, report CSV/XLSX, role restrictions, notification histories, desktop/mobile navigation and logout. The new fifth identity has EMPLOYEE membership in CI Alpha and FINANCE membership in CI Beta; switching changed navigation and Beta's SAP worklist remained empty. Mailpit checks found 2 employee reminders, 4 manager digests, 2 rejection notices, 2 approval notices, 6 SAP failure notices, 8 UNKNOWN notices and 2 Finance reminders, with exact recipient/subject/UTF-8 text matching persisted intents.

Phase 9 did not repeat the separate process-kill/restart experiment from Phase 8. Recovery coverage here uses existing expired-lease, claim-token, retry and reconciliation tests plus browser-driven real HTTP mock scenarios. GitHub-hosted runs require a later authorized commit/push; local success must not be described as a new GitHub run.

## Changed implementation areas

New files: V15__organization_ownership.sql, organization/application/OrganizationAccess.java, TenantFixtures.java, PhaseNineIsolationTest.java, common/api/ExceptionHandlerAdviceTest.java, frontend/src/app/organizations/page.tsx, frontend/tests/browser/organizations.spec.ts, docs/security.md and this report.

Updated areas: identity/security and conflict advice; employee/project/timesheet ownership mappings and repositories; approval/SAP/reporting authorization; SAP/notification claims and recipient queries; frontend BFF, shell and logout; explicit membership setup in all existing integration fixtures; CI/browser seed and local reporting seed; smoke tests; application/Compose/environment configuration; README, AGENTS, architecture, notification, reporting and SAP worker documentation. V1–V14, existing CI workflow definitions and the Phase 8 verification report were preserved.

## Phase 10 prerequisites and limits

Local verification passed; a GitHub-hosted run of these uncommitted changes remains outstanding. Review the legacy ownership decision and back up the target database before any migration. Organization creation, invitations, membership administration and onboarding remain trusted operator SQL operations; no public registration or administration UI exists. Add audited management workflows in a later phase. Review per-organization integration credentials/routing before real SAP or SMTP use. No deployment, push or merge is part of this task.
