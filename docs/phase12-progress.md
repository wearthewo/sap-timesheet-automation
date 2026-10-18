# Phase 12 production-readiness checkpoints

Branch: `feature/phase12-production-readiness`. Current checkpoint: 12D. 12A (`06ce4f5`), 12B (`9c3d1f5`) and 12C (`ae14b2e`) were committed before this checkpoint; their historical verification below is preserved. 12D changes remain uncommitted. No deployment, push, merge, legacy database migration or original Keycloak realm change is authorized here.

| Checkpoint | Status |
| --- | --- |
| 12A — repository audit and test reliability | Complete: isolated fixtures, full single-run browser pass and security regression verified |
| 12B — professional UX and dashboard | Complete: role dashboards, workflow UX, security tests and full single-run browser verification passed |
| 12C — security and performance | Complete for local verification: 409 backend tests, 28 smoke tests, 16 browser tests in one run, Mailpit and bounded performance checks passed; deployment risks documented |
| 12D — production infrastructure | Complete for local AMD64 preparation/verification; encrypted restore passed; ARM64 frontend and external launch checks remain unverified |
| 12E — final verification | Not started |

## 12A scope and decisions

Reviewed repository rules, README, architecture/security documentation, Phase 9–11 reports, the ignored live-QA report, CI jobs, Dockerfiles, Compose, browser tests and CI fixtures. Branch and working tree were clean at entry. Preserve Java 21, Node 24, pnpm, Keycloak, tenant-owned database roles, all migrations and business behavior.

- Onboarding creates its own collision organization using a second real registered user. Duplicate-slug, verification, private session, mobile/logout and password-recovery assertions remain.
- Seven seeded browser specs each receive independent accounts and two organizations. No worker mutates process-wide credentials or falls back to developer users. Two tests within a spec use independent months/exports or read-only data; the mobile timesheet scenario owns a pre-submitted month and no longer depends on the editing test.
- Fixed business dates and explicit expected states make fixtures reproducible; a unique run namespace prevents collisions across executions. The notification reminder period is captured from the persisted scheduler result rather than guessed from the browser clock.
- Browser execution uses two workers with full parallel scheduling and zero automatic retries. Per-test output paths and per-run output directories avoid artifact overwrites.
- `CI_RUN_ID` selects a validated directory under `.verification/`; separate local runs must also select distinct published ports. CI retains the `ci` default. Only a guarded `workforce-ci-<12 hex>` project can be managed. Fixture selection SQL scopes project codes by organization.
- No changes to application business logic, dependencies, migrations or original development environments.

## Verification commands and results

Local commands use the repository-scoped Node 24 runtime and existing Java 21 / Node 24 Docker runtimes; no global toolchain changes. Runtime evidence stays ignored under `.verification/phase12a/`.

| Check | Result | Evidence |
| --- | --- | --- |
| Backend security regression | PASS: 102 tests, zero failures/errors/skips | backend/tests.log and backend/reports |
| Frozen frontend install, lint, typecheck | PASS | frontend-static.log |
| Frontend production build | PASS during isolated Compose build | start.log |
| Standalone onboarding before CI seed | PASS: one test including self-created duplicate slug and recovery | onboarding-ready.log |
| Standalone mobile review before editing test | PASS: one test using its own submitted month | mobile-alone.log |
| CI helper guards / isolation / redaction | PASS: eight tests | helper-tests.log |
| GitHub workflow syntax | PASS: actionlint 1.7.7 | actionlint.log |
| Complete smoke suite | PASS: 26 tests, zero failures/skips | full-browser.log |
| Complete parallel browser suite | PASS: all 13 tests in one execution, 7.1 minutes, two workers, zero retries | full-browser.log |
| Exact Mailpit comparison | PASS: 51 delivered messages across all seven notification types | full-browser.log |
| Isolated Docker stack | PASS: all six services healthy; health/OIDC/OpenAPI/Swagger checks passed; diagnostics saved and only this run's containers/volumes removed afterward | start.log, diagnostics/services.txt, stop.log |
| GitHub-hosted run | Not run: no push authorized | local verification only |

Exact backend command (in a copied backend directory inside Java 21 Docker, separate PostgreSQL Testcontainers):

```sh
./mvnw -B -ntp test -Dtest=PhaseOneIntegrationTest,PhaseNineIsolationTest,PhaseTenOnboardingTest,PhaseElevenAdministrationTest,ExceptionHandlerAdviceTest '-DargLine=-Xmx1536m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1'
```

Frontend commands: `pnpm install --frozen-lockfile`, `pnpm lint`, `pnpm typecheck`, and Dockerfile `pnpm build`. Focused commands: `pnpm test:browser onboarding.spec.ts` and `pnpm test:browser timesheets.spec.ts --grep 'mobile employee'`. Full invocation: `CI_RUN_ID=phase12a CI_BROWSER_CONTAINER=workforce-browser-tests PLAYWRIGHT_WORKERS=2 node infra/ci/stack.mjs test` (PowerShell environment equivalents locally). This runs the unchanged smoke script through Node's test runner, `pnpm test:browser`, then the existing exact Mailpit verifier.

Helper command: `node --test infra/ci/ci.test.mjs`. Infrastructure commands: `CI_RUN_ID=phase12a node infra/ci/stack.mjs prepare`, `config`, `start`, `seed`, `test`, followed by `diagnostics`/`stop` after verification. Run ports are 23000/28080/28180/25432/28025/21025. Compose project: `workforce-ci-9537bce710b0`; original `workforce` and interactive `workforce-ci-98b4339864f0` environments are excluded.

Docker-backed commands required outside-sandbox execution. Original databases/realms were not migrated or reset. A first standalone local browser attempt launched before frontend readiness and failed to connect; bounded readiness polling was added to the ignored local focused runner, after which onboarding passed before any seed. This startup attempt is disclosed separately from the required complete suite run.

Mailpit exact comparisons passed for 14 employee reminders, five manager reminders, two rejections, two approvals, six SAP failure notifications, eight SAP UNKNOWN notifications and 14 finance reminders. Checks cover recipients, subjects, UTF-8 text, links, comments and ordinary deduplication. Ambiguous SMTP acceptance still cannot guarantee exactly-once physical delivery.

No remaining 12A blocker. The full backend suite was not rerun: this checkpoint ran the five relevant security/integration classes listed above. Mock SAP behavior was exercised through the full browser suite; its standalone unit suite was not rerun. Production load, real SAP, production SMTP and GitHub-hosted execution are not claimed by this local checkpoint. Changes remain uncommitted on the requested branch.

## Changed files

- `frontend/tests/browser/onboarding.spec.ts`: self-contained collision fixture, original assertions retained.
- `frontend/tests/browser/fixtures.ts` (new): required private per-spec manifest, no developer-credential fallback.
- `approvals`, `notifications`, `organizations`, `reports`, `sap-worker`, `sap`, `timesheets` browser specs: isolated fixture loading; unique artifact paths; separate mobile month.
- `frontend/playwright.config.ts`: full parallel scheduling, two workers, isolated output paths, zero retries preserved.
- `infra/ci/stack.mjs`, `support.mjs`, `ci.test.mjs`: guarded independent run directories, atomic ownership/consumption, per-spec seed, tenant-scoped SQL, private manifests, short-lived admin token renewal and pre-console redaction.
- `.github/workflows/e2e.yml`: upload the new safe per-run browser-results location; preserve triggers, permissions and cleanup.
- `README.md` and this progress file: usage, evidence and checkpoint boundaries.

## Audit findings reserved for later checkpoints

### 12B — usability and documentation

- Project/employee code links look like ordinary text; assignment and lifecycle actions are far below the list/detail headings. Users confused Archive with Save. Review discoverability and confirmation wording.
- Membership, employee profile linkage, manager relationship and accepted invitation are separate prerequisites. User testing needed manual diagnosis of an unlinked manager and a registered finance account with a pending invitation. Provide clearer setup status and next-step guidance; preserve the security boundaries.
- Personal dashboard versus organization/role-specific work needs review. Do not grant FINANCE/ADMIN manager-review overrides.
- Some documentation still claims no administration API/role endpoint despite Phase 11; update those passages during documentation cleanup, without rewriting historical verification results.

### 12C — security and performance

- Session access-token expiry requires reauthentication; encrypted-cookie session lifetime does not refresh the Keycloak token. Review user messaging and any future refresh design with expiry/logout/security tests.
- Public API docs are intentionally available locally; decide production network exposure. Registration/invitation abuse limits and per-organization quotas need explicit assessment beyond Keycloak login brute-force protection.
- Tenant authorization and composite FKs are implemented; no PostgreSQL RLS defense exists. Any new query/worker must retain explicit organization ownership. Already-authorized in-flight operations cannot be retroactively cancelled by membership changes.
- Reporting downloads are bounded but buffered in memory. No production-volume query plans, load test, concurrent-download memory measurements or tenant fairness test has been performed.
- `ProjectService.assigned` uses `LocalDate.now(ZoneOffset.UTC)` while business calendars use Europe/Athens. Audit the near-midnight assignment/dashboard behavior with an injected Clock regression in 12C; this timezone inconsistency was found by inspection, not changed in 12A.
- SMTP ambiguous acceptance can duplicate physical delivery. SAP requires reconciliation on ambiguous acceptance; preserve immutable snapshots and stable export identity.
- These are review items, not claims of newly reproduced vulnerabilities. Dependency scanning, session hardening and performance changes belong to 12C.

### 12D — production operations

- Compose uses Keycloak `start-dev`, HTTP localhost configuration and a shared local PostgreSQL role for the application/identity databases. Production requires deliberate TLS/issuer/proxy and least-privilege database configuration.
- Backups/restore drills, secret/signing-key rotation, production SMTP, monitoring/alerting and recovery/runbooks remain prerequisites. Real SAP company routing and tenant credentials are not designed or validated.
- Original populated development DB remains V14; any real migration needs a reviewed current ownership mapping and backup. Never infer legacy membership or enable `LEGACY_TENANT_CONFIRMED` without that mapping.
- Render remains an architectural target, not a deployed environment. No provider/infrastructure changes in 12A.

## Next checkpoint

12B is now explicitly requested. Complete its verification below and stop; 12C–12E remain out of scope. Preserve the isolated fixture manifest, single-use suite guard and zero-retry full-suite gate.

## Checkpoint 12B — professional UX and role-aware dashboard

### Implementation and decisions

- Read the attached request, AGENTS, README, architecture, Phase 11 verification, 12A progress and actual routes, BFF, styles and tests before edits. The branch was clean at entry, with 12A committed.
- Retained Tailwind/CSS, Node BFF, Keycloak and backend role enforcement. No new dependency, schema migration, cloud service or worker behavior.
- Added desktop sidebar, active-route indicators, organization context, direct sign-out, visible focus and wrapping mobile navigation. No hidden-only mobile account action.
- Dashboard separates organization setup (ADMIN), direct-report review/activity (MANAGER), personal time/rejected months (active employee profiles), and SAP operations/report shortcuts (FINANCE/ADMIN).
- New read-only `GET /api/v1/dashboard`: authorizes the active membership again, uses explicit organization predicates and a repeatable-read snapshot. Counts use SQL; rejected months and manager activity are each limited to five. No entire business dataset is fetched to compute a counter. ADMIN does not gain manager review access. Inactive/unlinked managers receive no team metrics.
- Finance counts include approved timesheets across all periods, consistent with the SAP worklist. Pending invitation counts exclude expired pending rows. Empty organizations have zero counts and setup actions, not demo content.
- Timesheets gain status explanations, date-specific project guidance, save/delete/reopen messages and conflict guidance. Input stays on conflict; explicit reload warns about losing unsaved input. Existing versions and submit/reopen policy remain unchanged.
- Approval pages explain direct reports and missing-manager setup; history remains append-only. Manager comments are preserved after a failed decision.
- SAP views explicitly say **Simulated · Mock SAP**. All operational states have explanations. Attempts and reconciliation are distinguished; safe identifiers/codes remain expandable. Retry/worker semantics remain unchanged.
- Reporting retains modes, calculations and BFF routes. Filters show friendly labels with unchanged enum values; CSV/XLSX errors display in place. Expected file type and safe filename are checked before browser download.
- Notifications separate historical intent from current business state and SMTP acceptance from delivery/reading. Types are human-readable, raw delivery codes remain available, and messages expand accessibly.
- Administration uses visible linked names, assignment/status jump actions and explicit archive-versus-save guidance. Account linkage, manager role, accepted invitation and dated assignments are separate prerequisites. Lifecycle confirmations and authorization remain intact.
- UI instants use en-GB / Europe/Athens with an explicit Athens label. Calendar work dates do not shift; inputs retain ISO values. Current-month defaults use Athens; dashboard month comes from the configured backend clock. Downloads retain UTC event timestamps. Hours are displayed from backend decimal values; no frontend hours arithmetic added.

### Changed files / APIs

- Backend: new `dashboard/api/DashboardController.java`, `dashboard/application/DashboardService.java`; dashboard security/isolation cases in `PhaseNineIsolationTest`.
- Frontend: workspace layout/navigation/styles/dashboard, display helpers, timesheet/current-month/history/editor, approvals, SAP views, reporting/download UI, notification history, administration lists/details/forms/assignments.
- Tests: new dashboard role/mobile/date coverage; administration, reporting and stale-tab conflict workflows extended; approval/onboarding assertions updated for intentional presentation changes without removing business assertions. Local Docker browser runner mounts source read-only for display-helper tests.
- Removed superseded `approvals/pending-count.tsx`; dashboard projection replaces it.
- README, architecture and this progress file. No migrations or dependencies changed.

### Verification

Evidence uses ignored `.verification/phase12b/` and `.verification/phase12b-final/`, isolated ports 23000/28080/28180/25432/28025/21025 and fresh CI Compose projects. Original V14 PostgreSQL/Keycloak and the interactive Phase 11 environment are excluded. No persistent volumes were reset. The first 12B stack was stopped with its volumes retained; the final run uses a different project and fresh volumes.

| Check | Observed result |
| --- | --- |
| Relevant backend security, onboarding, administration and tenant tests | 112 distinct tests passed across the initial run and corrected isolation-class rerun; details below |
| New dashboard projections | 10 added cases within the 33-test isolation class; all 33 passed on corrected rerun |
| Frozen frontend install, lint, typecheck | PASS on final source; phase12b-final/frontend-static.log |
| Frontend production build | Passed in both isolated Compose builds |
| CI helper guards | 8 passed |
| First complete browser execution | 8 passed, 7 failed; not an acceptance pass |
| Final smoke suite | PASS: 26/26, zero failures/skips |
| Final complete browser suite | PASS: 15/15 in one 6.6-minute execution, two workers, zero retries/skips |
| Exact Mailpit verification | PASS: 51 messages across seven types; recipients, subjects, UTF-8 bodies, links, comments and ordinary deduplication |
| Final isolated Docker stack | PASS: six healthy services; health/OIDC/OpenAPI/Swagger passed; redacted diagnostics captured |
| Responsive/keyboard checks | PASS: all four dashboard roles, skip-link/main focus, mobile overflow/logout, workflow screens; representative screenshots visually reviewed |
| Git whitespace check | PASS: git diff --check |
| GitHub-hosted run | Not run; no push authorized |

Backend command (Java 21 Docker, PostgreSQL Testcontainers):

```sh
./mvnw -B -ntp test -Dtest=PhaseOneIntegrationTest,PhaseNineIsolationTest,PhaseTenOnboardingTest,PhaseElevenAdministrationTest,ExceptionHandlerAdviceTest '-DargLine=-Xmx1536m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1'
./mvnw -B -ntp test -Dtest=PhaseNineIsolationTest '-DargLine=-Xmx1536m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1'
```

The initial 112-test run passed 110 and exposed two new test fixtures missing the schema-required `submitted_at` value. The fixtures were corrected; all 33 isolation tests then passed (zero failures/errors/skips). The other 79 tests passed in the initial run. This is not a claim of a single 112-test successful execution or a rerun of every backend test. The production backend package was built through the Dockerfile using `./mvnw -B clean package -DskipTests`.

Frontend commands: `pnpm install --frozen-lockfile`, `pnpm lint`, `pnpm typecheck`, `pnpm build`, `pnpm test:browser`. The local helper runs `node --test frontend/tests/smoke.test.mjs`, the exact script behind `pnpm test:smoke`. CI helper command: `node --test infra/ci/ci.test.mjs`.

Isolated stack commands: `node infra/ci/stack.mjs prepare`, `config`, `start`, `seed`, `test`, `diagnostics`, with `CI_RUN_ID=phase12b-final`, the six ports above, `CI_BROWSER_CONTAINER=workforce-browser-tests` and `PLAYWRIGHT_WORKERS=2`. `config` invokes `docker compose config --quiet`; `start` invokes `docker compose up --build -d --wait --wait-timeout 600` and bounded health/OIDC/OpenAPI/Swagger checks. Java/container, Compose and browser commands required outside-sandbox Docker access. Node 24 was repository-scoped; no global installation changed.

The first browser execution crossed an overnight interruption: Keycloak reported expired logout sessions, service deadlines were exceeded, and scheduled reminders existed on both October 14 and 15 (one successful attempt per daily intent). It also exposed obsolete assertions for the old ISO-only work-date heading and old dashboard heading. Assertions now check the calendar `time[datetime]`, manager-specific review section and new-organization setup/zero-employee state. No lifecycle, isolation, duplicate-slug, password-recovery or notification assertions were removed. Its fixtures were not reused for the final suite.

### Remaining scope / next checkpoint

Stop after 12B. Security hardening, load testing, infrastructure and final release verification remain 12C–12E. The existing assigned-project endpoint still evaluates today in UTC; it remains an explicit 12C audit item. Date-specific time entry is backend-authoritative. UI targets the requested Athens timezone; changing deployment business timezone later must include UI configuration review. Production SAP/email and exactly-once SMTP guarantees remain outside scope.

### Completion evidence and handoff

Checkpoint 12B is complete as of 2026-10-15. Final evidence: `.verification/phase12b-final/full-browser.log`, `frontend-static.log`, `start.log`, `seed.log`, `helper-tests.log`, `browser-results/` and `diagnostics/`. Backend evidence remains under `.verification/phase12b/backend/` and `backend-corrected/`. These ignored files contain synthetic verification data and are not intended for commits.

The final browser execution covered administration/invitation/assignment workflows, employee edits and stale-tab input preservation, manager rejection/resubmission/approval, four role dashboards, Athens date boundaries, tenant switching, registration/duplicate slug/password recovery, notification privacy and content, report authorization/downloads and in-page download errors, SAP success/permanent failure/retry/UNKNOWN reconciliation, mobile navigation and logout.

Mailpit comparison counts: 14 employee reminders, 5 manager reminders, 2 rejection notices, 2 approval notices, 6 SAP failure notices, 8 SAP UNKNOWN notices and 14 finance reminders. No ordinary duplicate delivery was observed. Ambiguous SMTP acceptance can still cause physical duplicates; this checkpoint makes no exactly-once SMTP claim.

Visual inspection covered employee/manager desktop and employee mobile dashboards, Admin desktop, Finance mobile, the responsive report and expanded mobile notification screens. Automated overflow and logout assertions also ran across administration, approvals, reports, notifications, SAP and timesheets. No new unresolved 12B defect remains. Production load, real SAP/SMTP and wider security hardening are not verified here.

The final stack `workforce-ci-d95aabd11f0b` is left running on the isolated ports for review. The earlier `workforce-ci-b43c0d680b69` containers are stopped, with volumes retained. Original `workforce` and interactive `workforce-ci-98b4339864f0` services/data were not changed. No migration, dependency, Keycloak realm configuration or worker implementation changed in tracked source.

Working branch remains `feature/phase12-production-readiness`; 12B changes are uncommitted and unstaged. Nothing was pushed, merged or deployed. Next checkpoint requires an explicit 12C instruction; begin with the security/performance audit items above, preserve the successful zero-retry browser gate, and continue to exclude original V14 data/realm changes without a separately reviewed migration decision.

### 12B review file inventory
- `backend/src/main/java/com/acme/workforce/dashboard/api/DashboardController.java`
- `backend/src/main/java/com/acme/workforce/dashboard/application/DashboardService.java`
- `backend/src/test/java/com/acme/workforce/PhaseNineIsolationTest.java`
- `docs/architecture.md`
- `docs/phase12-progress.md`
- `frontend/src/app/app/admin/actions.ts`
- `frontend/src/app/app/admin/admin-form.tsx`
- `frontend/src/app/app/admin/assignments.tsx`
- `frontend/src/app/app/admin/components.tsx`
- `frontend/src/app/app/admin/employees/[id]/page.tsx`
- `frontend/src/app/app/admin/employees/page.tsx`
- `frontend/src/app/app/admin/invitations/page.tsx`
- `frontend/src/app/app/admin/members/page.tsx`
- `frontend/src/app/app/admin/projects/[id]/page.tsx`
- `frontend/src/app/app/admin/projects/page.tsx`
- `frontend/src/app/app/approvals/page.tsx`
- `frontend/src/app/app/approvals/pending-count.tsx`
- `frontend/src/app/app/approvals/review.tsx`
- `frontend/src/app/app/layout.tsx`
- `frontend/src/app/app/navigation.tsx`
- `frontend/src/app/app/notifications/page.tsx`
- `frontend/src/app/app/page.tsx`
- `frontend/src/app/app/reports/monthly/downloads.tsx`
- `frontend/src/app/app/reports/monthly/page.tsx`
- `frontend/src/app/app/sap/[id]/page.tsx`
- `frontend/src/app/app/sap/export-panel.tsx`
- `frontend/src/app/app/sap/page.tsx`
- `frontend/src/app/app/timesheets/approval-history.tsx`
- `frontend/src/app/app/timesheets/current-month.tsx`
- `frontend/src/app/app/timesheets/page.tsx`
- `frontend/src/app/app/timesheets/timesheet-editor.tsx`
- `frontend/src/app/globals.css`
- `frontend/src/lib/display.ts`
- `frontend/src/lib/sap-view.ts`
- `frontend/tests/browser/administration.spec.ts`
- `frontend/tests/browser/approvals.spec.ts`
- `frontend/tests/browser/dashboard.spec.ts`
- `frontend/tests/browser/onboarding.spec.ts`
- `frontend/tests/browser/reports.spec.ts`
- `frontend/tests/browser/timesheets.spec.ts`
- `infra/ci/stack.mjs`
- `README.md`

## 12C — security hardening and performance (completed 2026-10-17)

12C began from clean commit `9c3d1f5` on `feature/phase12-production-readiness`. The preceding 12A/12B evidence is historical and unchanged; 12B was committed before this checkpoint. All 12C changes remain uncommitted. No original V14 database, original Keycloak realm, original volumes, deployment, push or merge is involved.

### Date inconsistency: diagnosis and correction

PostgreSQL assignment `DATE` columns map to Java `LocalDate`; Jackson emits ISO calendar strings, and Next.js forwards those strings unchanged. Date inputs and date-specific availability requests preserve `YYYY-MM-DD`. `calendarDate()` formats its UTC anchor in UTC deliberately, avoiding browser-local shifts; event timestamps continue to display in Europe/Athens. No browser date-string conversion defect was found.

The defect was `ProjectService.assigned()` choosing **today in UTC**, while the dashboard and business calendar used Athens. It now uses the injected Clock and configured business zone, consistent with the dashboard. JDBC administration projections retrieve `LocalDate` directly. Eight PostgreSQL/API regressions cover January, non-leap/leap February, a 30-day month, and both sides of spring/autumn DST at the UTC/Athens date boundary. Each verifies inclusive same-day validity, exclusion of the previous day, unchanged REST dates and PostgreSQL date retrieval. Existing date-specific timesheet validation remains authoritative.

### Security controls and audit

- Require the JWT `exp` claim in addition to existing issuer, audience, subject, signature and timestamp validation. A validly signed token without an expiry previously passed the configured validator. The focused test reproduced this before the fix.
- Bound authenticated API POST/PUT/PATCH bodies to 65,536 bytes before JSON binding or identity provisioning, including requests without Content-Length. Return structured 413 `REQUEST_TOO_LARGE`.
- Limit organization creation and invitation create/preview/accept to 60 requests per validated subject per minute, shared across organizations. Structured 429 includes Retry-After. Configuration: `API_MAX_REQUEST_BYTES`, `API_SENSITIVE_REQUESTS_PER_MINUTE`. Bounded 10,000-subject process-local storage, synchronized counters and expiration; no Redis or durable rate-limit tables. Replicas/restarts have independent budgets. This does **not** replace ingress/IP limits or Keycloak registration/login abuse protection.
- Bound the anonymous invitation-cookie handoff to 1 KiB, including streamed input. It retains exact Origin checks, strict token syntax, HttpOnly/SameSite cookies and HTTPS-dependent Secure. Oversized requests cannot set the invitation cookie.
- Explicit 64 KiB Server Action limit. Add a limited CSP (`base-uri`, `object-src`, `frame-ancestors`) and disable unused camera/microphone/geolocation permissions. This is not a strict script-nonce CSP or a TLS/HSTS deployment implementation.
- BFF retains encrypted server-side token cookies, PKCE/state, same-origin Server Actions, explicit logout Origin enforcement, fixed logout redirect and no browser-visible bearer/refresh tokens. Expired access tokens require reauthentication; no refresh-token redesign. Local HTTP cannot verify real HTTPS cookie/TLS behavior.
- Business routes revalidate active membership and verified email. Effective roles remain database-owned. Dashboard branches are tenant-scoped and role-specific; manager visibility remains active-profile/direct-report/no-self-review. No FINANCE/ADMIN approval override introduced.
- Invitations retain HMAC-generated bearer tokens with only hashes persisted; verified-email binding, expiration, same-actor idempotency, replay rejection and transactional membership/profile/consumption/audit. Concurrent acceptance regression now also asserts a single employee link/version increment and audit record. Existing concurrent last-admin demotion test preserves one active administrator through organization-row locking and database safeguards.
- SAP and notification processing retain persisted tenant identity, SKIP LOCKED, leases, fenced results, immutable attempts/snapshots, bounded retries and UNKNOWN reconciliation. No worker implementation changed. Existing recovery/concurrency regressions are rerun; SMTP ambiguous acceptance can still duplicate physical delivery.
- Existing parameter binding, sort allowlists, page/download bounds, safe errors and secret-free application logging remain. No public frontend/backend generic proxy or new CORS wildcard introduced. Deployment must restrict database, Keycloak administration, Mailpit, Mock SAP and diagnostic API exposure.

### Dependency review (2026-10-16)

`pnpm audit --prod --json` reported zero advisories both before and after the change. This was insufficient alone: the [Next.js October 14 advisory](https://nextjs.org/blog/nextjs-security-update-october-14-2026) identifies Server Action CPU denial-of-service, a feature this application uses. Updated Next.js and eslint-config-next 16.3.8 → 16.4.1, with the frozen lockfile preserved. The full npm audit retains one development-only `braces` advisory, [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm), reached through ESLint/fast-glob; audit reports no patched version. No untrusted glob pattern input is exposed by the application. Do not describe this as a completely vulnerability-free dependency tree.

[Keycloak 26.8.1](https://www.keycloak.org/2026/10/keycloak-2681-released) is a compatible security patch over 26.8.0. Only fresh isolated verification uses the new image; upgrading the original or production realm requires its own backup/migration review.

The OSV batch scan checked 111 resolved Maven runtime dependencies and initially flagged seven artifacts. Compatible overrides retain Boot 3.5.16/Java 21: Commons Lang 3.18.0, Log4j 2.25.5, Jackson BOM 2.21.7, Tomcat 10.1.61, PostgreSQL JDBC 42.7.12. The post-update OSV scan still checks 111 runtime dependencies and flags only Spring MVC 6.2.19 for the two feature-specific advisories below. This is not a full OS/container-image vulnerability scan.

Spring Framework 6.2 advisory exposure must be distinguished from reachability: this application uses JSON REST, not XsltView or server-rendered SSE fragments. Spring Security WebAuthn/distributed sessions, DPoP and authorization-server consent pages are not used. Native JPA queries do not accept client-provided Sort/Pageable; JDBC sort choices are allowlisted. Relevant primary advisories: [DPoP replay](https://spring.io/security/cve-2026-41707/), [WebAuthn](https://spring.io/security/cve-2026-47841/), [JPA sort validation](https://spring.io/security/cve-2026-47834/). These feature assessments do not restore upstream support. The Spring 3.5/6.2 line requires a supported-version strategy before public deployment; a major Boot migration or paid support is not silently introduced in 12C.

### Verification and resource measurements

Final results are recorded below. Evidence directories: `.verification/phase12c/`, `.verification/phase12c-final/` and `.verification/phase12c-serial/` (ignored; synthetic credentials must not be committed). Failed attempts are retained. All mandatory local gates passed in the final run; public deployment readiness remains subject to the documented 12D/12E work.

### Requirements for 12D

Do not start 12D within this checkpoint. Future work must explicitly decide supported Spring dependency strategy, TLS/secure-cookie and proxy trust configuration, ingress/IP rate and connection limits, registration abuse controls, private service exposure, CPU/memory limits, backup/restore and production SMTP/SAP configuration. Resource budgets below are preliminary, based on a bounded local exercise, not capacity guarantees.

### 12C verification log (intermediate runs retained)

- Reproduction run: `./mvnw -B -ntp test -Dtest=PhaseTwelveSecurityTest` before fixes: **9 failures / 9 tests**, zero errors/skips. Eight date-boundary expectations and the expiry requirement failed as intended.
- First complete backend run after core fixes: **406 passed**, zero failures/errors/skips; `./mvnw -B -ntp clean package -DskipTests` passed. This run preceded the final dependency overrides and three additional API integration assertions, so it is not the final patched-backend gate.
- Initial `docker compose up --build` failed resolving advisory-listed Tomcat 10.1.58: it is not published in Maven Central for the embedded artifacts. Corrected to the available compatible 10.1.61. The next build/start passed all six health checks and health/OIDC/OpenAPI/Swagger probes.
- First browser execution: **28/28 smoke**, **11/16 Playwright**, no retries or skipped tests. Administration/approval/dashboard/SAP-worker scenarios timed out during overlapping backend verification. The new security test additionally exposed a test-harness defect: Node-side Playwright APIRequestContext does not inherit Chromium's Docker hostname mapping. Fixed session checks to use browser fetch, and the direct cross-origin request to use the established host mapping. Privacy assertions return only booleans/keys, avoiding token values in failure output. No existing assertions or timeouts were weakened.
- The failed browser stack `workforce-ci-dc34cf27414d` was stopped with volumes retained. Its `.verification/phase12c/` evidence remains. A fresh `workforce-ci-e86c30dc3067` stack uses `.verification/phase12c-final/`. Its first build attempt encountered a Docker Hub DNS timeout; the retry uses the same sources and fresh, unconsumed fixtures. Final browser execution is sequenced after backend verification rather than run concurrently.
- Final standalone frontend `pnpm install --frozen-lockfile`, `pnpm lint`, `pnpm typecheck` passed with explicit exit status zero. `pnpm build` passed in the frontend builder image. The earlier Windows wrapper classified ordinary pnpm stderr as NativeCommandError despite a completed build; the explicit final exit status removes that ambiguity.
- `node --test mock-sap/server.test.mjs`: **7/7 passed**. `node --test infra/ci/ci.test.mjs`: **8/8 passed**. All used repository-scoped Node 24.
- Source review: dashboard/finance/admin reads use bounded projections and tenant-root queries; SAP worklists batch employees/users/exports/jobs instead of entity-per-row lookup. Reporting uses parameterized SQL, allowlisted sorting and bounded pagination/download snapshots. No index is added without measurement. Assigned-project availability reads date-valid assignments through the authorized employee ownership chain.

The second complete browser execution (`phase12c-final`) passed **15/16**, with **28/28 smoke**. The approval workflow encountered the workspace error boundary immediately after login; frontend diagnostics show a backend fetch timeout. This run was no longer concurrent with Maven. Local contention/cold-start latency is a hypothesis, not a proven sole cause. No business assertion or timeout was changed. Its disposable stack was stopped with volumes retained; a third fresh stack (`phase12c-serial`, `workforce-ci-e89655cbbe2f`) uses the supported `PLAYWRIGHT_WORKERS=1` override. This limits the parallel-browser evidence of the final gate; backend concurrency tests remain enabled.

### Final backend and static verification

All commands use Java 21 or Node 24, with pnpm 12.8.1. Docker-backed commands and dependency/network checks required outside-sandbox execution; no global toolchain installation was changed. Docker operations target disposable CI projects only.

| Command / check | Observed result |
| --- | --- |
| `./mvnw -B -ntp test '-DargLine=-Xmx1024m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1'` | **409 passed, 0 failed, 0 errors, 0 skipped**, 25m46s; final patched dependencies |
| `./mvnw -B -ntp clean package -DskipTests` | **PASS**, 50.127s, after the complete test run |
| `pnpm install --frozen-lockfile` | **PASS**, final lockfile |
| `pnpm lint` | **PASS** |
| `pnpm typecheck` | **PASS**, Next route generation and strict TypeScript |
| `pnpm build` | **PASS**, Docker frontend build stage |
| `node --test mock-sap/server.test.mjs` | **7 passed**, no failures |
| `node --test infra/ci/ci.test.mjs` | **8 passed**, no failures |
| `git diff --check` | **PASS**; Windows line-ending notices only |

Backend breakdown: Phase 1 **21**, Phase 2 **29**, Phase 3 **22**, Phase 4 **28**, Phase 5 **34**, Phase 6 **49**, Phase 7 **43**, Phase 8 business **8**, notification content **24**, SAP downloads **32**, Phase 9 isolation **33**, Phase 10 onboarding **29**, Phase 11 administration **26**, Phase 12 security/date **12**, API request limits **6**, SMTP adapter **10**, exception mapping **3**. All 17 suites passed. PostgreSQL Testcontainers exercises Flyway through V17; no migration was modified.

Evidence: `.verification/phase12c/final-backend/reports/`, `backend-tests.log`, `backend-package.log`, `frontend-final.log`, `frontend-image.log`, and the separate browser run directories. Credentials, manifests, runtime binaries and generated artifacts remain ignored and must not be committed.

### Security and reliability coverage

| Area | Evidence and result | Limit |
| --- | --- | --- |
| Calendar dates / Athens boundary | PhaseTwelveSecurityTest: eight real PostgreSQL/API cases pass; existing assignment/month/day-limit tests retained | Calendar dates are not instants; no conversion to Athens is applied to stored DATE values |
| JWT validation / oversized API requests | PhaseTwelveSecurityTest and existing identity tests pass; missing expiry rejected, anonymous requests still require authentication | Local Keycloak, not a production identity-provider penetration test |
| Subject request budgets | ApiRequestLimitsTest: concurrent counters, tenant-header bypass prevention, streamed bodies and bounded storage pass | Process-local budget; distributed/IP enforcement deferred to ingress |
| Tenant and dashboard privacy | PhaseNineIsolationTest: foreign/suspended memberships, role changes, direct reports, bounded owner history and all SAP states pass | No frontend-only authorization |
| Invitations / last active ADMIN | PhaseElevenAdministrationTest: hashing, verified-email binding, expiry/revocation/replay, simultaneous acceptance/account linking and concurrent admin demotions pass | Last-admin API uses soft status changes; no hard-delete administration endpoint |
| SAP / notification durability | PhaseFiveIntegrationTest, PhaseSevenIntegrationTest, PhaseNineIsolationTest pass: claims, fencing, tenant attribution, retry limits, reconciliation and transaction boundaries | Recovery exercised through persisted expired claims; **no new process-kill/restart experiment in 12C** |
| Reporting / protected downloads | Existing PhaseSix/PhaseEight/PhaseNine suites pass, including decimal values, immutable snapshots and role/tenant restrictions | No production SAP or external SMTP connectivity |
| Browser auth / cookies / CSRF | New security browser test checks private session output, HttpOnly/SameSite, cross-origin logout rejection and mobile logout; final full-run outcome recorded below | HTTP environment cannot prove Secure cookies, TLS, HSTS or real proxy behavior |

No new worker, persistence or authorization-policy redesign was needed. Do not interpret the audit or advisory feature analysis as a comprehensive penetration test or a guarantee of zero vulnerabilities.

### Final browser, Mailpit and Docker gate

`CI_RUN_ID=phase12c-serial`, `CI_BROWSER_CONTAINER=workforce-browser-tests`, `PLAYWRIGHT_WORKERS=1`; ports frontend 23000, backend 28080, Keycloak 28180, PostgreSQL 25432, Mailpit 28025/21025. Repository-scoped Node 24 ran `node infra/ci/stack.mjs prepare`, `config`, `start`, `seed`, `test`. The helper executes `docker compose config --quiet` and `docker compose up --build -d --wait --wait-timeout 600` with the base and CI override files, followed by bounded health/OIDC/OpenAPI/Swagger polling. Fresh credentials/fixtures are generated in the ignored run directory. Login and registration HTML readiness were also probed before seeding; no real account or authentication bypass was used.

- **All six services healthy**; health, OIDC discovery, OpenAPI and Swagger checks passed.
- **28/28 smoke tests passed**, zero skips/failures (7.45s). The local helper invokes the exact `test:smoke` script body, `node --test frontend/tests/smoke.test.mjs`, using Node 24 rather than the host pnpm installation.
- **16/16 Playwright tests passed together in one execution (10.2m)**, one worker, **zero retries/skips**. The container runs `pnpm test:browser`. This is a fresh complete run, not a sum of selectively rerun tests.
- Covered registration/email verification/password recovery/duplicate slug, invitations and administration, tenant switching, all role dashboards, employee edit/delete/totals/submit, rejection/reopen/resubmit/approval, finance reports and CSV/XLSX/SAP downloads, export/retry/reconciliation, role restrictions, notifications, session privacy, cross-origin logout rejection and desktop/mobile logout.
- Mailpit exact-content verification passed **51 messages**: employee reminders **14**, manager reminders **5**, rejection **2**, approval **2**, SAP FAILED **6**, SAP UNKNOWN **8**, finance reminders **14**. The existing verifier compares recipient, subject and UTF-8 plain-text body (including names, links/comments) to durable intent and requires one matching delivery. HTML is absent by current contract. Ordinary deduplication passed; ambiguous SMTP acceptance remains explicitly outside an exactly-once guarantee.
- Neither GitHub-hosted CI nor HTTPS/production infrastructure was executed here. Tests used the local Docker Node 24/Java 21 runtimes outside the normal sandbox. The original `workforce-*` and interactive `workforce-ci-98b4339864f0-*` services were not stopped/recreated; only disposable QA stacks were stopped, with volumes retained.

### 12C changed files

New:

- `backend/src/main/java/com/acme/workforce/common/infrastructure/ApiRequestLimits.java`
- `backend/src/test/java/com/acme/workforce/ApiRequestLimitsTest.java`
- `backend/src/test/java/com/acme/workforce/PhaseTwelveSecurityTest.java`
- `frontend/tests/browser/security.spec.ts`

Modified:

- `.env.example`, `compose.yaml`
- `backend/pom.xml`
- `backend/src/main/java/com/acme/workforce/common/infrastructure/SecurityConfiguration.java`
- `backend/src/main/java/com/acme/workforce/identity/infrastructure/JwtConfiguration.java`
- `backend/src/main/java/com/acme/workforce/project/application/ProjectService.java`
- `backend/src/main/resources/application.yaml`
- `backend/src/test/java/com/acme/workforce/PhaseElevenAdministrationTest.java`
- `frontend/next.config.ts`, `frontend/package.json`, `frontend/pnpm-lock.yaml`
- `frontend/src/app/api/invitations/context/route.ts`
- `frontend/tests/browser/dashboard.spec.ts`, `frontend/tests/smoke.test.mjs`
- `docs/phase12-progress.md`

No migrations, CI workflows, original data, original realms or Docker volumes were rewritten/deleted. No deployment, commit, push or merge. Changes stay on `feature/phase12-production-readiness` for review.

### Bounded performance measurements

After the successful business/Mailpit gate, the isolated `workforce-ci-e89655cbbe2f` database received two additional synthetic organizations with **400 linked employees, eight projects, 9,600 APPROVED timesheets and 38,400 decimal-hour entries**. Four 5.75-hour entries sum to 23.00 hours per sheet. The generated volume is a query benchmark, not an additional verified approval lifecycle: it does not generate dense approval/export histories. Existing fixtures and both development databases remain intact. The benchmark used `statement_timeout=30000`, a short committed fixture transaction, `ANALYZE`, and read-only `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` statements afterward.

| Representative SQL statement | Planning | Execution | Result rows |
| --- | ---: | ---: | ---: |
| Monthly reporting BASE CTE, count and approved total | 49.321 ms | 26.403 ms | 1 |
| Monthly reporting BASE CTE, first 20 employee rows | 106.470 ms | 8.133 ms | 20 |
| Tenant APPROVED SAP worklist, first 20 rows | 11.366 ms | 1.302 ms | 20 |
| Tenant finance dashboard export-count join | 15.656 ms | 7.529 ms | 1 |

These are individual representative SQL plans, not every statement in each endpoint or a latency guarantee. Report pagination used `uk_employee_number`, user/employee primary keys and `uk_timesheet_employee_month`; the SAP worklist used `ix_timesheets_org_status`. Aggregates selected sequential/hash scans at this dataset size. The small approval/export tables also used sequential scans; do not extrapolate that choice to dense history. Lateral decision lookups are within one SQL statement, not one application round trip per employee. Source review found bounded projections/batch loading rather than entity-per-row network queries; no full APM/query-count profile was performed. No index/schema change was justified by these measurements.

Authenticated Chromium then fetched complete SSR responses through the BFF with **10 measured reads per route, concurrency two**, after one warmup per route (30 measured requests plus three warmups). All completed with HTTP 200 and no login/workspace-error response. No SAP export or mutation was timed.

| Route | Median | Sample p95 / maximum |
| --- | ---: | ---: |
| `/app` | 365.10 ms | 468.40 ms |
| `/app/reports/monthly?year=2026&month=10` | 693.30 ms | 952.70 ms |
| `/app/sap` | 403.45 ms | 523.70 ms |

With only ten samples, nearest-rank p95 equals the maximum; this is **not** a statistically established service percentile. It excludes cold startup/login, sustained concurrency, write-heavy workloads and production network latency. Earlier cold/shared-host browser timeouts remain relevant despite these warm read results.

`docker stats --no-stream` sampled all six services six times per idle/load window, about ten seconds apart. The load window brackets a short burst and may miss transient peaks. These are Docker container memory figures, not separately measured Java heap/native memory or OS RSS. SAP/notification workers share the backend JVM and cannot be assigned separate memory from this measurement.

| Service | Idle sampled range (MiB) | Load-window sampled range (MiB) |
| --- | ---: | ---: |
| Spring backend + workers | 381.4–381.6 | 381.6–383.7 |
| Keycloak | 567.4–569.5 | 567.5–567.8 |
| PostgreSQL | 141.9–143.1 | 142.1–146.8 |
| Next.js | 138.0–138.0 | 138.0–142.9 |
| Mock SAP | 19.27–35.74 | 19.27–22.90 |
| Mailpit | 20.98–29.19 | 20.98–20.99 |

CI limits remained backend 1,536 MiB/768 MiB heap, Keycloak 1,024 MiB/512 MiB heap, PostgreSQL 512 MiB, Next.js 768 MiB/512 MiB Node heap, Mock SAP/Mailpit 256 MiB each. Final inspection: **six healthy services, zero restarts and no OOM kills**. The original V14 PostgreSQL/Keycloak containers retained their 13-day uptime; the existing interactive verification stack remained running. No volume deletion or database reset occurred.

Commands: repository-scoped Node 24 ran `.verification/phase12c/performance-seed.mjs`, `resources.mjs idle-resources`, and `resources.mjs load-resources`; `docker run --rm --init --ipc=host` with the existing `workforce-browser-tests` image executed `/work/performance.mjs` from the mounted `performance-browser.mjs`. These Docker-dependent probes required outside-sandbox execution. Safe numeric evidence is in `phase12c-serial/query-plans.json`, `performance-http.json`, `idle-resources.json` and `load-resources.json`; the fixture manifests remain private/ignored.

### Preliminary VPS resource envelopes (planning estimates)

These are **budgets, not measured requirements or throughput/user-capacity promises**. They assume one application stack, API and workers in the same JVM, and reserve space beyond the small local dataset. They do not authorize purchasing or deploying infrastructure.

| Allocation | 8 GiB host | 12 GiB host |
| --- | ---: | ---: |
| OS, reverse proxy, operational tools | 1.00 GiB | 1.50 GiB |
| Spring API plus in-process workers | 1.50 GiB | 2.00 GiB |
| Keycloak | 1.25 GiB | 1.50 GiB |
| PostgreSQL | 1.50 GiB | 2.50 GiB |
| Next.js BFF | 0.75 GiB | 1.00 GiB |
| Mock SAP and Mailpit, if retained for staging | 0.50 GiB | 0.50 GiB |
| Unallocated headroom / filesystem cache | 1.50 GiB | 3.00 GiB |
| Total | **8.00 GiB** | **12.00 GiB** |

JVM heaps must remain below container budgets to leave native/metaspace/thread headroom. A separate worker process would add another JVM and requires a revised budget. Production SAP and SMTP are external services; Mailpit is not a production mail service. Builds, full Testcontainers suites and browser runners should not share the serving host's normal budget. Confirm CPU, disk latency, backups and real workload on a dedicated staging host before selecting limits. Local Docker reported 16 CPUs and 8,256,671,744 bytes (~7.69 GiB), while two untouched development stacks also remained running; this is not equivalent to a dedicated 8 GiB VPS benchmark.

### Remaining risks and next checkpoint

- Resolve the Spring support/advisory strategy before public exposure. Two Spring MVC advisories affect features not used here, but that is not a substitute for maintained dependencies. The unpatched `braces` finding is development-only. Container OS packages have not received a comprehensive vulnerability scan in this checkpoint.
- Retest HTTPS Secure cookies, TLS/HSTS, forwarded-header trust, reverse-proxy limits and Keycloak production startup configuration in 12D. Current Compose deliberately runs local Keycloak development mode.
- Add ingress/IP and registration abuse controls; application subject limits are process-local and cannot prevent anonymous connection exhaustion or coordinated accounts. Do not expose PostgreSQL, Mailpit, Mock SAP, administrative consoles or diagnostics publicly by default.
- The two-worker browser runs encountered timeouts on this shared local host. A serial full-suite pass does not prove parallel-browser stability or acceptable cold-start behavior under production load. Preserve those failure artifacts and repeat bounded cold/warm concurrency measurements on dedicated staging infrastructure.
- SMTP ambiguous acceptance may cause duplicate physical delivery. Expired-claim/fencing tests do not establish behavior for every real process, disk or network failure. No new process termination experiment, production SMTP test, real SAP connection or user-capacity test was performed in 12C.
- 12D must cover backup/restore verification, secrets/rotation, health probes, resource limits and private networking. 12E remains the final release gate. **Do not begin either checkpoint automatically.**

Final Git state: branch `feature/phase12-production-readiness`, HEAD `9c3d1f5`; **15 modified tracked files and four new untracked source/test files**, all unstaged/uncommitted. `git diff --check` passes. Original migrations V1–V17 are unchanged. Checkpoint **12C is complete for local verification**, with the limitations above; 12D and 12E are not started.

## 12D — production infrastructure, backups and operations (2026-10-17–18)

**Completed for infrastructure preparation and local AMD64 verification, with an explicit ARM64 frontend verification gap. This is not public-deployment approval. 12E has not started.** Entry branch was `feature/phase12-production-readiness`, clean at `ae14b2e` (committed 12C). Earlier 12A–12C results above are historical and unchanged.

### Implementation and boundaries

Added an independent production definition under `infra/production/`: Caddy HTTPS ingress, Next.js BFF, Spring API/workers, optimized production Keycloak, PostgreSQL with separate database owners, and private persistent Mock SAP. Only proxy TCP 80/443 is published. Local synthetic bindings were 127.0.0.1:24080/24443. No Mailpit or new queue service in production; existing local/CI Compose files remain unchanged.

Images pin multiarchitecture base-index digests in `images.lock.json`. Application containers run nonroot with dropped capabilities/no-new-privileges, bounded resources/logs and graceful-stop allowances. PostgreSQL's official initialization drops to its database user; the one-shot backup helper has only CHOWN and DAC_READ_SEARCH (to read owner-only Mock SAP data through a read-only mount). Caddy's inherited file capability is removed at build time so it can execute with all runtime capabilities dropped.

Named runtime file secrets are outside Git/build contexts. No actual secret value occurs in final container Config.Env or captured stdout/stderr logs. Host root/Docker administrators can still access mounted files/process environments; this is not a vault. Runtime schema/role/approval/SAP/notification behavior is unchanged. No Flyway migration was added or rewritten.

Caddy fixes public hosts and forwarded headers, blocks administration/master/management paths, issues correlation IDs, limits request body/read times, and removes request/response-header objects from logs. Keycloak uses production start, persistent PostgreSQL, fixed HTTPS issuer/callbacks, exact confidential PKCE client, verified email and existing brute-force/password controls. Real SMTP is only prepared: authenticated required STARTTLS and hostname verification, synthetic disabled host during QA; **no external email sent**.

### Verification matrix

| Scenario | Observed result | Status / evidence |
|---|---|---|
| Production env and Compose | Dummy required secrets, host validation and config --quiet passed | PASS; validate-env.mjs, production.test.mjs, verify.mjs config |
| New production regression checks | 10 passed, zero failed/skipped | PASS; infra/production/production.test.mjs |
| Existing CI helper regressions | 8 passed, zero failed/skipped | PASS; infra/ci/ci.test.mjs |
| Existing Mock SAP tests on Node 24 host | 7 passed, zero failed/skipped; combined final helper/mock run 25/25 | PASS; mock-sap/server.test.mjs |
| AMD64 builds | Backend Maven package, frontend frozen install/build, proxy, Keycloak, Mock SAP and backup image built | PASS; ignored build logs |
| Frontend lint / typecheck | pnpm lint and pnpm typecheck both exit 0 in pinned Node24 build image | PASS; frontend-checks.log |
| Backend security regression | 51 tests: PhaseTwelveSecurityTest 12, PhaseNineIsolationTest 33, ApiRequestLimitsTest 6; zero failures/errors/skips; 4:39 | PASS; Testcontainers PostgreSQL, security-reports |
| HTTPS frontend smoke | Existing suite 28/28, zero skips/failures (6.22s), local CA trusted through NODE_EXTRA_CA_CERTS | PASS; existing smoke.test.mjs |
| Production-mode browser | 16 checks passed in the final execution: proxy restrictions, issuer, login/callback, tenant onboarding, Secure/HttpOnly/SameSite cookies, token privacy, CSRF rejection, mobile logout and fresh credential prompt afterward | PASS; browser-check.mjs, browser-result.json |
| HTTPS edge controls | HTTP308 to canonical HTTPS, private paths404, oversized request413, forwarded-header spoof resistance and query-marker log redaction passed with local CA trusted | PASS; edge-check.mjs |
| Private endpoints | Backend readiness/liveness/OpenAPI/Swagger 200; unauthenticated dashboard and Mock SAP rejected | PASS; internal-check.mjs |
| Actual imported identity settings | SSL required, verified email, exact HTTPS callbacks, restricted scope and authenticated STARTTLS settings verified through private admin API | PASS; internal-check.mjs; no SMTP connection/delivery |
| Service health and exposure | Six healthy; only proxy mapped to host; final containers no OOM and restart count zero after deliberate recreations | PASS; health-resources.json |
| Encrypted backup | Both DB dumps + Mock SAP copied while writers stopped, encrypted to age archive; only encrypted archive/checksum persisted outside tmpfs | PASS; backup.log |
| Empty-target restore | Both DBs' public-table counts/content hashes match; three organization memberships and all 17 successful Flyway migrations restored | PASS; restore.log and restore-result.json |
| Restore guard | Second restore into populated target rejected before overwrite | PASS; verify-backup.mjs |
| Mock SAP recovery | Restored identity lookup succeeds; explicit duplicate request returns HTTP409 | PASS; duplicate-check.mjs; synthetic record only |
| Shell syntax | bash -n backup.sh ops.sh offsite.sh monitor.sh | PASS in backup image; host timer/wrapper orchestration not installed |
| ARM64 base manifests | All six pinned base indices list linux/amd64 and linux/arm64 | PASS for manifest availability only |
| ARM64 backend | Image builds; Temurin Java21.0.12.1 executes under emulation | PASS for build/JVM startup; full ARM backend stack not exercised |
| ARM64 Mock SAP | Image builds, Node24.18.0 reports arm64; final HTTP/persistence suite 7/7 | PASS under Docker emulation, not native Oracle |
| ARM64 frontend | Dependency installation stalled with extremely slow npm downloads; stopped after >8h rather than claiming success | NOT VERIFIED; build-arm64-frontend.log. Native/bounded rerun required before ARM deployment |
| Public TLS / cloud host | Local CA only; no real ACME, DNS, renewal or Oracle/Hetzner execution | NOT TESTED |
| Offsite disaster recovery / alerts | SSH encrypted-transfer helper, retention and operator runbooks prepared; no remote destination, systemd timer or external alert delivery configured | NOT TESTED; local restore is not offsite DR |
| Full business suite / full browser suite | Not rerun in 12D; prior 409-backend/16-browser Phase12C results remain historical | NOT CLAIMED as 12D results; checkpoint12E remains separate |

The disposable projects were `workforce-prod-qa-acce6074d6` and `workforce-prod-qa-acce6074d6-restore`. The latter started only PostgreSQL and Mock SAP for restore validation. Synthetic identities/data and private evidence remain ignored under `.verification/phase12d/`. The original `workforce-*`, interactive `workforce-ci-98b4339864f0-*` and 12C `workforce-ci-e89655cbbe2f-*` data/realms/volumes were not modified or reset. No `down -v`, cloud deployment, purchase, commit, push or merge occurred.

### Failures and corrections, including reruns

- Initial test regex incorrectly treated a mounted NEXTAUTH_SECRET declaration as a plaintext environment value. Narrowed it to environment entries; no secret handling was weakened.
- Readiness originally searched for UP anywhere, incorrectly accepting Keycloak's overall DOWN response with one UP subcheck. Changed both Java probes to require HTTP200; regression added.
- Initial static proxy .2 collided with a dynamically allocated service address. Moved the six-service topology's proxy to .254 and updated Keycloak's trusted address; no volume reset.
- Caddy initially failed to execute with all runtime capabilities dropped because the upstream binary carried a file capability. Removed that capability at image build; retained nonroot/capability restrictions.
- First browser probes required an explicit navigation wait and longer bounded assertions on this shared host. One later probe overlapped the deliberate backup maintenance stop and failed connection refusal; it was not a valid application failure. The final browser run was performed separately and passed.
- One cold post-login workspace request hit the existing 10s BFF timeout while the shared host was busy. A subsequent warm run progressed; no timeout or business validation was weakened. Dedicated-host cold-start/load validation remains a launch gate.
- HTTPS logout reproducibly returned Forbidden with the new Caddy no-referrer policy. Matched the frontend's established strict-origin-when-cross-origin policy; regression now verifies the actual form Origin, unchanged cross-origin rejection, successful logout and new credential prompt.
- First backup failed reading mode0600 Mock SAP state with all helper capabilities dropped. The helper now has DAC_READ_SEARCH, with the source still mounted read-only; failed backup did not produce a success marker, and writers were restarted. The corrected backup/restore passed.
- ARM Mock SAP image initially hit a registry EOF; retry built it. ARM frontend downloads then stalled and were stopped; this remains a disclosed verification gap.
- ARM emulation exposed two existing Mock SAP tests that aborted after 30ms before the request was actually transmitted. Replaced that wall-clock assumption with bounded synchronization on body consumption/handler execution, then abort the response. All original duplicate/lookup/status assertions remain. Initial ARM result 5/7; corrected host and ARM runs each 7/7. Production Mock SAP business behavior was not changed.
- One manual runtime probe had a PowerShell quoting error for a Node expression; separate process.arch/--version commands passed. PowerShell labels native stderr as NativeCommandError in redirected files even for successful eslint commands; exit codes and test summaries, not that wrapper label, determine success.

### Exact commands and runtime evidence

The host Node24 executable was `.verification/runtime/node-v24.18.0-win-x64/node.exe`; no global installation changed. Docker commands used outside-sandbox execution. Production browser runner is the existing `workforce-browser-tests` image (its Node24.21.0 runtime stays within Node24); production Node base is pinned24.18.0.

```sh
node infra/production/resolve-images.mjs
node infra/production/verify.mjs prepare
node infra/production/verify.mjs config
node infra/production/verify.mjs build
node infra/production/validate-env.mjs --env-file .verification/phase12d/production.env --local-test
node infra/production/verify.mjs start
node --test infra/production/production.test.mjs infra/ci/ci.test.mjs mock-sap/server.test.mjs

docker build -f infra/production/caddy.Dockerfile -t workforce-proxy:phase12d-qa .
docker build -f infra/production/mock-sap.Dockerfile -t workforce-mock-sap:phase12d-qa .
docker build -f infra/production/backup.Dockerfile -t workforce-backup:phase12d-qa .
docker build --target build -f infra/production/frontend.Dockerfile -t workforce-frontend-checks:phase12d .
docker run --rm workforce-frontend-checks:phase12d sh -c 'pnpm lint && pnpm typecheck'
docker buildx build --platform linux/arm64 --load -f infra/production/backend.Dockerfile -t workforce-backend:phase12d-arm64 .
docker buildx build --platform linux/arm64 --load -f infra/production/mock-sap.Dockerfile -t workforce-mock-sap:phase12d-arm64 .
docker buildx build --platform linux/arm64 --load -f infra/production/frontend.Dockerfile -t workforce-frontend:phase12d-arm64 .
docker run --rm --platform linux/arm64 --entrypoint java workforce-backend:phase12d-arm64 -version
docker run --rm --platform linux/arm64 --entrypoint node workforce-mock-sap:phase12d-arm64 -p process.arch
docker run --rm --platform linux/arm64 --entrypoint node workforce-mock-sap:phase12d-arm64 --version
docker run --rm --platform linux/arm64 --entrypoint node -v "$PWD/mock-sap:/checks:ro" workforce-mock-sap:phase12d-arm64 --test /checks/server.test.mjs

node infra/production/verify-backup.mjs
node infra/production/verify-health.mjs
git diff --check
```

The build helper runs `docker compose -p <QA project> --env-file .verification/phase12d/production.env -f infra/production/compose.yaml build <service>` sequentially; start runs `up -d --wait --wait-timeout 600`. Build commands now have a 20-minute bound; Compose helper calls a 12-minute bound. The first ARM frontend attempt used the separate buildx command above, before that helper safeguard.

The backend image executed `./mvnw -B -ntp clean package -DskipTests`. The frontend image executed `pnpm install --frozen-lockfile` and `pnpm build`. Security tests used the pinned Maven21 image, Docker socket and existing dependency cache, copied source into an isolated /work, then:
```sh
./mvnw -B -ntp test -Dtest=PhaseTwelveSecurityTest,PhaseNineIsolationTest,ApiRequestLimitsTest '-DargLine=-Xmx1024m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1'
```
Docker test runner: `--memory=2g -e MAVEN_OPTS=-Xmx512m -e JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v /var/run/docker.sock:/var/run/docker.sock -v workforce-maven-cache:/root/.m2`, source mounted read-only and reports mounted under ignored evidence. These Testcontainers databases were unrelated to application databases.

Browser command used `docker run --rm --init --ipc=host --network workforce-prod-qa-acce6074d6_ingress`, mounted browser-check.mjs at /work/production-check.mjs and ignored QA material at /qa, then `workforce-browser-tests node /work/production-check.mjs`. Chromium ignored only the synthetic local-CA certificate warning; separately, all28 HTTPS smoke tests used the copied **public** root certificate through NODE_EXTRA_CA_CERTS, without disabling TLS verification. Internal checks ran internal-check.mjs with the production Node image on the private services network. edge-check.mjs ran with that image on the ingress network and NODE_EXTRA_CA_CERTS=/qa/root.crt; its query marker was then checked absent from proxy stdout/stderr. No browser trace or authentication action link was retained.

Backup keys were generated in the ignored directory using `workforce-backup:phase12d-qa` with `--entrypoint age-keygen -o /qa/restore-key.txt`, then public recipient extraction `age-keygen -y -o /qa/recipients.txt /qa/restore-key.txt`. verify-backup.mjs stopped/restarted only the disposable writers, used backup.compose.yaml, created a separate empty restore project, added restore.compose.yaml only for decryption and ran the populated-target refusal check. An additional explicit duplicate-check.mjs probe verified HTTP409 from restored Mock SAP. Shell scripts passed bash -n in the backup image. Actual host systemd/flock/offsite orchestration remains prepared, not installed/tested.

### Resource sample, costs and remaining gates

Final single post-restart sample: Caddy31.11MiB, Next52.48MiB, Spring/workers367.6MiB, Keycloak488.4MiB, PostgreSQL75.71MiB, Mock SAP20.69MiB; approximately1.01GiB combined. This is **not a load benchmark, peak-memory measurement or capacity guarantee**. Multiple untouched development stacks shared Docker Desktop's ~7.69GiB host. Cold startup and emulation were slow. Container ceilings total5.5GiB; the [deployment guide](production-deployment.md) gives preliminary8/12GiB budgets and provider caveats.

Oracle Always Free capacity/availability and idle-reclamation rules must be checked at provisioning; no PAYG. Hetzner total must fit EUR15/month including tax/IP/domain/SMTP/offsite; no purchase or price guarantee. Neither cloud was accessed. Native ARM frontend/full-stack verification, maintained dependency/advisory strategy (including prior Spring findings), OS-image scanning, anonymous ingress abuse controls, public DNS/ACME renewal, real SMTP sender/DNS/delivery, installed backup/alert timers and independent offsite restore remain Phase13 gates. SMTP ambiguous acceptance can still duplicate physical delivery. No exactly-once claim.

### Changed files and next checkpoint

Modified: `.github/workflows/ci.yml` (adds production configuration tests), `.gitignore`, `README.md`, `docs/architecture.md`, `docs/phase12-progress.md`, `mock-sap/server.test.mjs` (deterministic network-response-loss tests).

New:
- `.dockerignore`.
- `infra/production/`: compose.yaml, backup.compose.yaml, restore.compose.yaml; six Dockerfiles (backend/frontend/mock-sap/keycloak/caddy/backup); Caddyfile; images.lock.json/resolve-images.mjs; workforce-realm.json; postgres-init.sh/with-secrets.sh; production.env.example/validate-env.mjs; backup.sh/manifest.sql/ops.sh/operations.sql/offsite.sh/monitor.sh; production.test.mjs; verify.mjs/browser-check.mjs/internal-check.mjs/edge-check.mjs/verify-backup.mjs/verify-health.mjs.
- [Production deployment](production-deployment.md), [environment reference](production-environment.md), [Keycloak production](keycloak-production.md), [backup/restore](backup-restore.md), [operations](operations.md), [Phase13 checklist](phase13-deployment-checklist.md).

No changes to AGENTS, migrations, backend/frontend application logic, original realms or original/CI Compose files. New root dockerignore excludes private verification/environment/key/dump artifacts. GitHub-hosted runs of the modified CI gate were not executed.

Final working tree contains **six modified tracked files and 38 new files**, all unstaged. git diff --check passes. Leave all12D changes **uncommitted** on `feature/phase12-production-readiness`, HEAD `ae14b2e`. Review the diff and architecture verification gap. Next checkpoint is **12E only when explicitly requested**; do not treat this report as authorization to start it or deploy.
