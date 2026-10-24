# Phase 12E release-readiness verification

Status: **Local checkpoint reviewed on 21 October 2026; Phase 12 acceptance is INCOMPLETE.** The original lost-click registration defect is demonstrated and fixed; three consecutive administration repetitions passed. The latest complete browser run passed 15/16 with a separate initial page-load timeout, tracked below as **P12E-CI-001**. Aggregate Mailpit verification passed independently. Disk headroom has now recovered; no full suite or build was repeated for this checkpoint. A local preservation commit is authorized and does not constitute merge, deployment or public-launch acceptance.

## Scope and protected data

Branch `feature/phase12-production-readiness`, entry HEAD `dc03a3f`, initially clean. Reviewed AGENTS, README, architecture/security, Phase 9–11 reports, historical 12A–12D results, production Docker/Compose, Keycloak, backup/operations and GitHub workflows. Historical checkpoint results remain intact.

No public deployment, purchase, DNS change, push, merge, original realm change or original database migration was performed. The original `workforce-postgres-1` returned Flyway **14** in a SELECT-only check before the disk interruption. It was not restarted or migrated afterward. This report does not certify its post-interruption data health. Original and GUI volumes were retained.

The legacy ownership report applies only to its particular snapshot. Migrating populated V14 data requires a fresh reviewed ownership mapping and disposable rehearsal; `LEGACY_TENANT_CONFIRMED` remains false. Fresh production must start with clean databases and a new realm, without synthetic QA identities. Realm import alone does not upgrade an existing realm.

## Changes and demonstrated defects

1. Added repository-scoped LF rules for Dockerfiles, YAML, Caddy and SQL, preserving existing shell/Maven LF and Windows CMD CRLF rules. A real `git -c core.autocrlf=true checkout-index` test verifies tracked Linux inputs without changing global configuration or the real index. Caddy working-copy line endings were normalized without routing changes.
2. Fixed restore diagnostics: an inaccessible PostgreSQL target was previously reported as nonempty. The count query's exit status is now checked first; restore still fails closed. Four executable shell regressions and two actual disposable-target refusal probes cover the behavior. Updated the restore runbook and CI.
3. Fixed onboarding field loss after a duplicate organization slug error. The full browser suite exposed an empty name. Blocking application JavaScript bundles reproduced the failure deterministically in 33.1 seconds against the unchanged image: the server returned the expected conflict, but client-only state reset. The server action now returns submitted name/slug with the error and controlled inputs initialize from those returned values. The browser test covers both pre-hydration and hydrated submissions, preserving all duplicate-slug, password-recovery and isolation assertions. No authorization or backend validation was moved into Next.js.

No backend code, dependency versions, lockfiles, migrations or architecture were redesigned.

## Verification matrix

PASS denotes executed verification; historical evidence is labelled separately. Raw logs, credentials, keys, database backups and fixture state remain ignored under `.verification/` and must not be committed.

| Scenario | Observed result | Evidence / limitation |
| --- | --- | --- |
| Full backend regression | PASS: **409 tests, 17 classes, 0 failures, 0 errors, 0 skips**, one complete fresh execution | `resumed/backend-reports/`, `backend-totals.json`; Java 21, PostgreSQL Testcontainers |
| Production backend packaging | PASS | `resumed/backend-tests.log`; 58.114 seconds |
| Frozen frontend installation | PASS after form fix | `resumed/reviewed-frontend-install.log` |
| ESLint / TypeScript / production build | PASS after form fix | `resumed/reviewed-frontend-{lint,typecheck,build}.log` |
| Full browser suite, latest complete run | FAIL: 15 passed, 1 initial `/login` load timeout; two workers, zero retries, zero skips | `registration/full-suite.log`; JUnit: 16 tests, 1 error, 0 failures, 0 skips; 272.331 seconds |
| Focused administration after hydration fix | PASS: three consecutive repetitions, zero errors/skips/retries | `registration/focused-stable-three-results/browser.xml`; 266.531 seconds; three executions of one scenario, not three new unique tests |
| Earlier serial browser run | INCOMPLETE/FAIL: 10 recorded passes, 1 failure, 5 without confirmed outcomes | `resumed/serial-local-test.log`; preserved historical run, no final XML |
| Fresh serial confirmation after hydration fix | BLOCKED / NOT RUN | 3.45 GiB free, below existing 4 GiB guard; stopped before prepare/seed/tests |
| Latest frontend smoke suite | PASS: 28, zero failures/skips | `registration/full-suite.log`; 3.917 seconds |
| Mailpit browser paths / aggregate comparison | PASS: both notification browser cases; independent aggregate 51 exact messages across seven types | `registration/mail-after-run.log`; browser command failed, so comparison was explicitly executed separately without claiming overall suite success |
| Production configuration tests | PASS: 10 | `resumed/quality-final.log` |
| CI helper tests | PASS: 8 | Same log |
| Mock SAP HTTP/persistence suite | PASS: 7 | Same log; simulator, not real SAP |
| Windows-style LF checkout | PASS: 1 test, 50 tracked Linux inputs | Same log; no global Git setting changed |
| Linux shell syntax | PASS: all seven shell scripts | `resumed/shell-syntax.log` |
| Restore guard regressions | PASS: four cases | `resumed/backup-guards.log` |
| Actual restore refusal probes | PASS: unreachable and populated targets | `resumed/actual-guards.log`; no writes/decryption |
| Actionlint / workflow syntax | PASS after final CI serial-worker change | `resumed/workflow-final-result.json`, exit 0; GitHub-hosted execution not performed |
| Production Compose | PASS: `config --quiet` | Disposable production environment only |
| Six production services | PASS: healthy, no OOM flags | `resumed/health-resources.json`; only proxy publishes ports |
| Private application probes | PASS: readiness/liveness/OpenAPI/Swagger 200; anonymous API/SAP 401 | `production-internal.log` |
| HTTPS proxy boundary | PASS: canonical HTTP 308, private paths 404, oversized body 413, forwarded-header spoof rejected | `production-edge.log`; synthetic local CA |
| Encrypted two-database restore | PASS: all public-table count/content hashes match | `resumed/restore-result.json`; fresh disposable target |
| Restore history / idempotency | PASS: 4 memberships, 17 migrations, decisions, exports, attempts, notifications; Mock lookup and duplicate rejection | No reconstruction from mutable records |
| Production dependency audit | npm: 0 advisories; Maven: documented conditional Spring findings | Runtime inventory matched all 111 audited coordinates |
| Container OS audit | PARTIAL; findings below | Frontend/backend/Keycloak scanned; remaining images not certified |
| Native cloud / public TLS / real SMTP / offsite recovery | NOT TESTED | Phase 13 requirements; no deployment |

Backend coverage includes authentication, tenant isolation, dashboard security, administration, invitations, last-admin protection, timesheet concurrency, manager decisions, SAP leases/fencing/retry/reconciliation, reporting/downloads, notifications, migrations and date boundaries. Passing the suite does not establish load capacity or eliminate all possible vulnerabilities.

## Browser execution and failures

The first complete two-worker run used fresh isolated fixture namespaces and completed **15/16** tests; onboarding field preservation failed. Its 28 smoke tests passed. The aggregate Mailpit verifier did not run after that failure, although individual notification browser tests passed. These results are retained separately in `resumed/ci-resume-test.log` and `.verification/phase12e-parallel/browser-results/`.

A first additional probe using a JavaScript-disabled context timed out during context shutdown; it was inconclusive and replaced by blocking Next.js bundles on the existing page. That replacement reproduced the original field-loss assertion before the fix. No assertion was removed or relaxed. The failed disposable stack was stopped with `docker stop`, retaining its volumes and diagnostics. It was not the original development or GUI stack.

The second complete run used `CI_RUN_ID=phase12e-final`, project `workforce-ci-a9aa65d0cc8c`, ports 43000/48080/48180/45432/48025/41025, two workers and zero retries. It finished 15/16 (5.9 minutes): pre-hydration preservation passed, but uncontrolled form reset replaced a newly corrected slug. The final correction retains controlled fields initialized from returned server values. Focused onboarding passed in 49.0 seconds (43.7-second test), including both submission modes and password recovery.

The third fresh full run (`CI_RUN_ID=phase12e-reviewed`, project `workforce-ci-ab9251015f2f`, reusing freed ports 43000/48080/48180/45432/48025/41025) completed **15 passed, 1 failed in 5.4 minutes**, with two workers and zero retries. Corrected onboarding passed in 38.2 seconds. Administration timed out waiting for Keycloak's `#firstName` registration field. Resource contention was suspected but not established. All 28 smoke tests passed.

One final fresh run, `CI_RUN_ID=phase12e-serial`, used **one worker and zero retries**, ports 33000/38080/38180/35432/38025/31025. A registry EOF blocked its initial rebuild before seeding. The runner then reused the exact already-built corrected image IDs, recorded in `resumed/serial-verified-images.json`, through an isolated override with `up -d --no-build --pull never --wait --wait-timeout 600`. Startup and fresh seeding passed. No source or lockfile change was made to work around the registry.

Serial smoke passed **28/28**. The browser log records **10 passes and one administration failure**; five remaining tests have no confirmed completion. The administration failure again waited 20 seconds for `#firstName`; its snapshot remained on the invitation page with **Create an account** focused. The root cause is unresolved and cannot be attributed solely to parallelism. The wrapper timed out after an approximately 23-hour execution gap (19 October 11:02 UTC to 20 October 09:58 UTC); diagnostics also timed out. No final browser XML was generated. Neither the gap nor the incomplete results are counted as a passing suite.

CI now requests one worker conservatively, but **serial stability is not established**. Assertions, test timeouts and retry policy were not weakened. Both notification browser cases, reporting, organization switching, corrected onboarding and the first SAP-worker case passed within that partial run; they do not substitute for a complete suite. The aggregate persisted-intent/Mailpit comparison was not reached. No further broad rerun was started. A final process inspection found no remaining Node runners matching Phase 12E, Playwright or the CI stack helper.

## Registration follow-up: demonstrated cause (20 October)

The user reported successful manual organization creation, invitation-link navigation, NEW Keycloak account registration and an authenticated ADMIN dashboard. This is useful manual evidence, not a substitute for the automated direct-report/invitation assertions. The administration scenario additionally logs out of an ADMIN session and immediately follows a MANAGER invitation in the same browser context; it subsequently repeats that path for an EMPLOYEE.

A fresh instrumented run of the unchanged application reproduced the original failure in **49.9 seconds**. Sanitized navigation evidence records:

- Earlier registrations from `/login` had React attached and sent the normal NextAuth and Keycloak registration requests.
- The failing path was `/invitations/accept` → `/invitations/review`. At the click, `document.readyState` was `interactive` and the button had **no attached React props/handler**.
- No NextAuth/OIDC request followed that click. Twenty seconds later the single browser page was still on `/invitations/review`, showing **Join your organization**, with **Create an account** focused. It was not an unexpected popup, wrong locator, failed Keycloak redirect or authorization rejection.
- Cookie metadata showed the HttpOnly, SameSite=Lax invitation cookie and residual Keycloak authentication-session cookies, but no Workforce session token after logout. Cookie values, JWTs and query/fragment action tokens were not recorded.
- Read-only realm inspection confirmed registration, email verification, password recovery and brute-force protection enabled; the confidential client's standard flow and S256 PKCE configured; both sign-in and registration callbacks present. The recent Keycloak log window included other authentication errors, but the failing click never reached Keycloak, so those events are not attributed to it.

**Cause:** an application hydration race exposed by Playwright's immediate action after the client-side invitation redirect. The server rendered an enabled client-only auth button before its handler was ready. A human completing the same path after hydration would not encounter that lost click. This matches [Playwright's hydration guidance](https://playwright.dev/docs/navigations#hydration).

**Targeted correction:** the shared SignIn control uses a false server snapshot and true hydrated client snapshot through `useSyncExternalStore`; it remains disabled until hydration, then retains existing pending/error behavior. OIDC endpoints, callback URLs, PKCE/state, session/tenant authorization and Keycloak settings are unchanged.

The administration test now holds only the review page's JavaScript behind a promise, asserts both auth controls are disabled, releases the scripts and continues the original complete workflow. Against the unchanged image this regression failed in **45.6 seconds**, receiving an enabled control. No sleeps, retries, raised timeouts, skipped tests or relaxed assertions were introduced. The diagnostic loader initially failed before collecting tests because its private wrapper used `import.meta` under CommonJS; correcting the wrapper did not change application behavior.

Safe diagnostic evidence is under `.verification/phase12e/registration/`: `navigation-before.json`, `realm-inspection.json`, `artifacts/` and `regression-before/`. Existing evidence and databases were preserved.

### Follow-up verification and remaining gate

Frozen installation, ESLint, TypeScript and production build all passed after the correction. The corrected frontend Docker image built successfully. A new isolated six-service project, `workforce-ci-6a993af7f575`, became healthy on ports 43000/48080/48180/45432/48025/41025. Backend/Mock images were reused by inspected immutable image ID. The first setup tried nonexistent project-specific image tags and stopped before creating services; it was corrected to inspect the existing containers' image IDs. No credentials, realm configuration or business rules were changed.

The first post-fix repetition batch began after an approximately 6.6-hour gap from startup. It finished **one pass, two failures in 3.3 minutes**: initial Keycloak navigation exceeded 20 seconds, then a later repetition received the safe BFF `SERVICE_UNAVAILABLE` page before invitation handling. Logs confirmed the latter 503. Their exact resource/network cause was not established; the long idle interval alone is not proof of causation. The third repetition passed the complete administration workflow. These failures remain under `focused-results/` and `focused-three.log`.

A separately recorded batch then passed **all three repetitions in 4.4 minutes**, one worker and zero retries. Each created unique accounts/organization and exercised the delayed-hydration assertion plus original registration, invitation, last-admin, employee/project/assignment, approval, SAP, mobile/logout and replay checks. No timeouts were raised and no assertions weakened.

Fresh per-spec fixtures were seeded once. The complete **two-worker** suite passed 28 smoke checks, then **15 of 16 browser cases in 4.5 minutes**. Administration timed out on its initial `page.goto("/login")` waiting for `load`, before registration or any invitation click. This differs from the proven lost-click defect; the snapshot showed the auth buttons correctly disabled before hydration. It remains an unresolved navigation/load reliability finding, not evidence that the original hydration correction failed.

The aggregate Mailpit comparison was subsequently run explicitly against this run's scoped persisted intents: **51 exact recipient/subject/plain-text UTF-8/body matches**, including links, comments and ordinary deduplication. Counts: TIMESHEET_REMINDER 14, APPROVAL_REMINDER 5, TIMESHEET_REJECTED 2, TIMESHEET_APPROVED 2, SAP_EXPORT_FAILED 6, SAP_EXPORT_UNKNOWN 8, FINANCE_MONTHLY_REMINDER 14. This separate pass does not turn the failed browser suite into a successful execution.

One final fresh serial attempt was planned. Only the completed project's six containers were stopped, with **all volumes retained**. The existing disk-space guard stopped the new attempt before prepare/seed/service creation because C: had **3.45 GiB free**, below 4 GiB. The frontend build cache was only 0.05 GiB (all `.next` 0.09 GiB); nothing was deleted and the guard was not lowered. Original databases, current port-33000 user environment and all previous QA evidence were preserved.

**Remaining prerequisite:** recover adequate host disk headroom (at least the existing 4 GiB guard, preferably more for Docker growth), then execute a fresh isolated full one-worker suite and its aggregate Mailpit check. Do not reuse consumed monthly/export fixtures or sum partial runs into success. CI remains at one worker conservatively; parallel stability is not established. No further broad rerun is pending in this session.

Follow-up commands, using scoped Node 24/pnpm and outside-sandbox Docker/browser access:

```sh
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm build
docker build -f frontend/Dockerfile -t workforce-frontend:phase12e-registration frontend
pnpm exec playwright test administration.spec.ts --repeat-each=3
# Above command executed in two separately recorded batches; neither used retries.
# Fresh isolated CI_RUN_ID=phase12e-registration:
node infra/ci/stack.mjs prepare
node infra/ci/stack.mjs config
# Startup used a recorded local-image override, up --no-build --pull never --wait --wait-timeout 600.
node infra/ci/stack.mjs seed
# PLAYWRIGHT_WORKERS=2; retries remain zero:
node infra/ci/stack.mjs test
node infra/dev/verify-phase8-mailpit.mjs .verification/phase12e-registration/intents-after-failed-browser.json
```

Private runners preserve logs/results in `.verification/phase12e/registration/`; no trace, credential value or action-link query was captured by the navigation diagnostic. Backend code was unchanged, so the earlier complete 409-test result is retained as earlier evidence, not presented as a new run.

## ARM64 investigation

The registration follow-up does not rerun or alter these architecture results.

12D stalled during `pnpm install --frozen-lockfile`, before Next compilation. Bounded 12E requests to the same registry URLs succeeded:

| Request | AMD64 | Emulated ARM64 |
| --- | ---: | ---: |
| Package metadata | 1.513 s | 23.948 s |
| Next tarball, 43,677,546 bytes | 31.664 s | 37.017 s |
| ARM SWC tarball, 32,761,684 bytes | 18.861 s | 24.710 s |

All returned HTTP 200. This establishes registry access at that time and emulation overhead; it does not prove a successful application build. No lockfile or integrity check was disabled.

Final bounded results (`resumed/architecture-results.json`):

| Target/check | Result | Scope |
| --- | --- | --- |
| AMD64 production frontend image | PASS, 99.692 seconds | Final corrected source; image loaded locally |
| ARM64 frontend | TIMEOUT at six-minute caller bound | Frozen install passed in 2m18.9s, including ARM SWC; Next production compilation began but no completed image/runtime result |
| ARM64 backend image | PASS, 12.994 seconds | Cached build layers; not a fresh ARM compilation benchmark |
| ARM64 Java startup | PASS, 13.884 seconds | `java -version`; not full Spring/DB/business verification |
| ARM64 Keycloak image | TIMEOUT at three-minute caller bound | Base layers downloaded/extracted; `kc.sh build` began; child log continued after caller timeout, with no verified completed image |
| ARM64 frontend/Keycloak health and complete stack | NOT TESTED | No usable verified frontend/Keycloak build from these attempts |

Caller timeouts are not proof that every Docker daemon build stopped immediately. Later log output is not a completed-image result. The architecture driver itself exiting successfully means it recorded these outcomes, not that all builds passed. Native Oracle compatibility remains **UNVERIFIED**. The original frontend download stall was not reproduced in the final attempt; emulated compilation and Keycloak augmentation remained bounded verification gaps, with no demonstrated native dependency incompatibility.

The disposable AMD64 production frontend was refreshed successfully from the new image. An additional HTTPS browser probe could not start because the local `workforce-browser-tests` image was absent and its pull was denied. This is **NOT TESTED**, not an application assertion failure. The earlier production edge/internal results below remain scoped to their recorded execution; they were not rerun after that refresh.

## Production security and operations

Executed disposable production-mode checks verified imported SSL/verified-email/STARTTLS settings, API authentication, proxy routing, private endpoint isolation, request-size handling and HTTPS with a trusted synthetic CA. Configured secret values were absent from container `Config.Env` and collected logs. This limited check is not a proof against every possible logging path. Authenticated BFF/CSRF/tenant/role rules are also exercised by backend and browser tests.

Actual public-domain ACME issuance/renewal, production SMTP delivery and sender DNS, external firewall/SSH policy, offsite transfer/restore and operator alert delivery remain untested. Authenticated API throttling plus Keycloak login brute-force protection does not fully cover anonymous registration/recovery abuse. Public ingress protection is a launch gate.

The same modular-monolith worker preserves PostgreSQL durable claims, fenced tokens, bounded retries, stable identities and UNKNOWN reconciliation. Backend tests exercise expiry/recovery deterministically. 12D restart/stop-start evidence is preserved in the progress report; **12E did not add a controlled process-kill-after-claim experiment**. An accidental host/disk interruption is not counted as a successful crash-recovery test.

SMTP ambiguous acceptance may duplicate physical delivery. No exactly-once guarantee is made. Mock SAP remains a simulator; no real SAP credentials or external email were used.

## Backup and recovery

The encrypted archive was created before disk exhaustion. The first disposable restore target had zero public tables but incomplete PostgreSQL initialization: local readiness passed while network authentication rules were absent. The operation failed closed. That target was preserved and a separately initialized `workforce-prod-qa-acce6074d6-restore12e2` restored successfully.

Both Workforce and Keycloak databases matched all-public-table row-count/content-hash manifests. Independent checks counted four organization memberships and all 17 Flyway migrations. Synthetic historical approvals, immutable SAP payload/attempts and notifications were preserved. Restored Mock SAP found the stable identity and returned 409 on duplicate creation. A second restore into the populated target was refused.

The successful restore used the previously built helper; the subsequent change only improves failure diagnostics. The rebuilt helper separately passed actual inaccessible/populated target refusal probes. Decryption keys remained restore-only; archives, keys and credentials remain ignored. Offsite scripting is prepared, but an installed schedule, remote credentials, retention, alert delivery and independent-host disaster recovery are **not verified**. No cross-database atomic-restore guarantee or measured production RPO/RTO is claimed.

## Resource and infrastructure limits

One idle production-mode sample: Caddy 13.40 MiB, Next.js 72.40 MiB, Spring/workers 355.60 MiB, Keycloak 472.20 MiB, PostgreSQL 66.84 MiB, Mock SAP 35.27 MiB; total approximately **1,015.71 MiB**. No OOM flags were present. This is not peak memory, sustained throughput, user capacity or cloud performance evidence.

Container ceilings total 5.5 GiB. Preliminary 8/12 GiB budgets must include OS/cache, backups, builds and disk headroom. Windows disk exhaustion interrupted the initial backend run after 330 tests and left a disposable database partially initialized. Those 330 tests are not added to the successful 409-test total. Only a regenerable frontend `.next` cache was removed by verification; no original volume was deleted. After space became available, resumed runners required at least four GiB free before heavy stages.

A later roughly four-hour execution gap caused CI startup and a waiting architecture helper to time out before tests/builds began; a second overnight gap likewise timed out startup after service health, before seeding; the exact host-pause cause was not proven. Startup resumed without consuming fixtures twice. The final fresh stack also hit a Docker registry EOF while resolving the Maven image; a bounded retry progressed past it. Windows subsequently refused SMTP binding on port 51025 before the third stack was seeded. Its ignored QA configuration was moved to the known-working freed ports of the stopped prior test stack; no original service or global Windows setting changed. These infrastructure failures are not hidden as successful test runs.

## Dependency support, advisories and licensing

Resolved runtime versions: Spring Boot 3.5.16, Framework 6.2.19, POI 5.5.1, springdoc 2.8.17, Log4j 2.25.5, Jackson 2.21.7, Tomcat 10.1.61, PostgreSQL JDBC 42.7.12; Next 16.4.1, React/ReactDOM 19.3.0, NextAuth 4.24.15, server-only 0.0.1. Java 21, Node 24.18 and pnpm 12.8.1 remain unchanged. The fresh Maven inventory exactly matches all 111 audited runtime coordinates.

- **BLOCKER:** [Spring Boot 3.5.16 ends OSS support](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/). MVC 6.2.19 has [XsltView](https://spring.io/security/cve-2026-47884/) and [functional plain-text SSE](https://spring.io/security/cve-2026-59313/) findings. The app uses neither feature, but that does not restore maintenance. Establish a maintained free dependency line through a planned Boot 4 migration/regression, or an explicitly funded support decision. No unsafe Framework override or unrequested major upgrade was made.
- npm production audit: zero advisories. Full tree: one HIGH development-only `braces` advisory, GHSA-vfj7-8cjw-p6xm. The application does not accept user glob expressions through ESLint. Track a compatible tooling fix separately; do not call the full tree clean.
- Next 16.4.1 includes the [October 14 fixes](https://nextjs.org/blog/nextjs-security-update-october-14-2026). [Node 24.21](https://nodejs.org/en/blog/release/v24.21.0) is available; a further [October 21 security release](https://nodejs.org/en/blog/vulnerability/october-2026-security-releases) was announced but not yet available at this review. Recheck before launch.
- **BLOCKER:** partial Trivy 0.76.0 OS scans require remediation/disposition and rescan. Frontend: 88 Debian packages, 251 advisory/package occurrences (4 critical, 64 high, 96 medium, 83 low, 4 unknown). These are not 251 unique exploitable flaws. [Debian's zlib assessment](https://security-tracker.debian.org/tracker/CVE-2023-45853) excludes Bookworm's binary from the vulnerable contrib/minizip build; Perl/PCRE package updates still require review. Backend had 59 and Keycloak 51 occurrences, neither HIGH/CRITICAL in these OS-only reports. PostgreSQL/proxy/mock/backup scans were incomplete after interruption. No blanket suppression was added.

Preliminary license inventory: Next/React/server-only MIT, NextAuth ISC; Spring/POI/Keycloak/Caddy Apache 2.0; PostgreSQL its own license; Node MIT with bundled notices; OpenJDK GPLv2 with Classpath Exception. Container packages add GPL/LGPL and other obligations. Preserve notices. This is not exhaustive legal certification. No top-level application LICENSE was found; settle distribution licensing before publishing reusable source/images. No paid Spring support is assumed within the budget.

## Exact commands and environment

Backend used pinned Java 21 Maven Docker image `maven:3.9.12-eclipse-temurin-21@sha256:c3c9d3ac4ce8431a3995c0318b8d390f448e693dd4fabc16e9b68d2e1f3d7b46`, copied source, Docker socket for independent PostgreSQL Testcontainers, Maven cache, 2 GiB memory, `MAVEN_OPTS=-Xmx512m`, `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2` and `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`. No application database was mounted. Inner commands:

```sh
./mvnw -B -ntp test '-DargLine=-Xmx1024m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1'
./mvnw -B -ntp clean package -DskipTests
./mvnw -B -ntp dependency:list -DincludeScope=runtime -DoutputFile=/evidence/runtime-dependencies.txt
```

The complete backend run took 16:10. Reports were copied before clean packaging.

Frontend used repository-local Node 24.18 and pnpm 12.8.1, without global installation changes:

```sh
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm build
pnpm exec playwright install chromium
pnpm test:smoke
pnpm test:browser
pnpm exec playwright test onboarding.spec.ts  # focused failing reproduction, separately recorded
```

The initial private runner incorrectly assumed pnpm's old JavaScript entrypoint and failed before installation. It was corrected to pnpm 12's native executable. Lockfile integrity was preserved.

```sh
node --test infra/production/line-endings.test.mjs infra/production/production.test.mjs infra/ci/ci.test.mjs mock-sap/server.test.mjs
bash infra/production/backup-guards.test.sh
# Each of the seven shell scripts separately: bash -n <script>
node infra/ci/stack.mjs prepare
node infra/ci/stack.mjs config
node infra/ci/stack.mjs start
node infra/ci/stack.mjs seed
node infra/ci/stack.mjs test
node infra/ci/stack.mjs diagnostics
```

CI start runs `docker compose up --build -d --wait --wait-timeout 600`; config runs `docker compose config --quiet`. Tests run smoke, Playwright, then exact persisted-intent/Mailpit comparison; the latter was not reached in the final run. Production probes used `internal-check.mjs`, `edge-check.mjs`, `verify-health.mjs` and `verify-backup.mjs` against disposable QA. Actionlint 1.7.7 ran in a read-only network-disabled container. A listed command is not itself evidence of success; use the matrix.

Final architecture commands, with the bounded outcomes above:

```sh
docker buildx build --platform linux/amd64 --progress=plain --load -f infra/production/frontend.Dockerfile -t workforce-frontend:phase12e-amd64 .
docker buildx build --platform linux/arm64 --progress=plain --load -f infra/production/frontend.Dockerfile -t workforce-frontend:phase12e-arm64 .
docker buildx build --platform linux/arm64 --progress=plain --load -f infra/production/backend.Dockerfile -t workforce-backend:phase12e-arm64 .
docker run --rm --network none --platform linux/arm64 --entrypoint java workforce-backend:phase12e-arm64 -version
docker buildx build --platform linux/arm64 --progress=plain --load -f infra/production/keycloak.Dockerfile -t workforce-keycloak:phase12e-arm64 .
```

Docker, dependency downloads, browser installation/execution and security-audit network calls required outside-sandbox execution. Local edits/Git inspection did not. GitHub-hosted workflows were not run because no push is authorized.

## Release decisions and remaining work

| Decision | Result |
| --- | --- |
| A: Phase 12 code/infrastructure ready for review/merge | Reviewed changes may be preserved in a local checkpoint commit; **NO-GO for completion/merge sign-off** until a complete browser run passes and GitHub CI confirms the reviewed changes. Aggregate Mailpit passed separately. P12E-CI-001 remains open; recovered disk space alone does not close it. |
| B: Begin controlled Phase 13 deployment | **NO-GO from this checkpoint** until the regression gate and target-architecture decision are resolved and deployment is explicitly authorized. AMD64 infrastructure is prepared with substantial local verification, not an unconditional release approval. |
| C: Open public registration | **NO-GO** |

**BLOCKER before public launch:** complete successful browser/notification acceptance; maintained Spring support; review/remediate release-image findings and rescan; anonymous registration/recovery abuse controls; unresolved ownership mapping if migrating populated data. Unverified ARM64 blocks selecting Oracle ARM64, not an independently verified AMD64 target.

**REQUIRED FOR PHASE 13:** approved cloud/account/capacity choice; native resource checks; domain/DNS and real TLS/renewal; production secret provisioning/rotation; SSH/firewall; real SMTP sender/DNS/delivery; offsite encrypted backup/restore and retention; operational alert delivery; disk monitoring; clean production identities; full production smoke tests. No deployment was started.

**NON-BLOCKING with follow-up:** development-only dependency finding, documented SMTP duplicate ambiguity, load/capacity testing beyond these bounded local checks. No throughput/user-capacity guarantee is made.

Budget is at most EUR15/month total. The [Phase 13 checklist](phase13-deployment-checklist.md) records a conditional Hetzner calculation (EUR8.49 net base, assumed 24% VAT gives EUR10.53 before IPv4) and unquoted domain/SMTP/offsite costs. Oracle Always Free capacity is not guaranteed. Neither provider was purchased/provisioned and the final monthly total is not verified.

## Changed-file inventory and Git

- `.gitattributes`, `.github/workflows/ci.yml`, `.github/workflows/e2e.yml`, `README.md`.
- `infra/production/Caddyfile` (line endings only), `backup.sh`, new `backup-guards.test.sh`, new `line-endings.test.mjs`.
- `frontend/src/app/onboarding/actions.ts`, `create-form.tsx`, `frontend/tests/browser/onboarding.spec.ts`.
- `frontend/src/app/login/sign-in.tsx`, `frontend/tests/browser/administration.spec.ts` (registration follow-up).
- `docs/backup-restore.md`, `docs/phase12-progress.md`, `docs/phase13-deployment-checklist.md`, this new report.

Before checkpoint staging, **17 working-tree paths** were reviewed on `feature/phase12-production-readiness`, parent `dc03a3f`: 14 modified tracked files and three new files. Caddy's working-copy normalization has no semantic Git diff, so the checkpoint contains 16 changed files. The local commit accompanying this update preserves the reviewed work; use Git history for its hash. Private verification material, secrets, backups and QA databases are excluded. No push, merge or deployment is authorized. Phase 12 acceptance remains incomplete and Phase 13 has not started.

## Local preservation checkpoint — 21 October 2026

The user reports that VHDX compaction is complete and the existing application at `http://localhost:33000` works with its existing data. A read-only host disk check measured approximately **60.09 GiB free**, resolving the previous 3.45 GiB disk-headroom blocker. This checkpoint did not independently inspect database contents or certify post-compaction database integrity. No Docker volumes, databases, realms or running services were changed; the six-service environment was not recreated.

Reviewed the complete working diff, authentication hydration guard, delayed-JavaScript regression, onboarding error-state preservation, restore refusal behavior, CI changes and artifact exclusions. The guard keeps auth controls disabled until their React handlers exist; OIDC/session behavior and backend authorization are unchanged. The tests retain registration, duplicate-slug, recovery, role and workflow assertions. No backend code, migrations, dependency versions or lockfiles changed. No credentials, database backups or generated QA artifacts are included in the checkpoint. Dependency and image findings earlier in this report are historical audit results, not a fresh security scan on 21 October.

Only these lightweight checks were run for this checkpoint, using the existing repository-scoped Node 24.18.0 executable and Git for Windows Bash:

```text
# Repository root; node resolves to .verification/runtime/node-v24.18.0-win-x64/node.exe
node --test infra/production/production.test.mjs infra/production/line-endings.test.mjs infra/ci/ci.test.mjs
# frontend working directory, same scoped Node executable
node node_modules/eslint/bin/eslint.js src/app/login/sign-in.tsx src/app/onboarding/actions.ts src/app/onboarding/create-form.tsx tests/browser/administration.spec.ts tests/browser/onboarding.spec.ts
# Repository root; C:/Program Files/Git/bin/bash.exe
bash -n infra/production/backup.sh
bash -n infra/production/backup-guards.test.sh
bash infra/production/backup-guards.test.sh
git diff --check
git diff --cached --check
```

Results: **19/19 Node tests passed**, zero failures/skips; targeted ESLint passed for all five changed frontend files; both shell syntax checks passed; **4/4 executable restore-guard cases passed**. Restore tests use mocked database/secret commands and never access a real database. The LF test exercises a temporary Windows-style Git checkout without changing global Git configuration. A repeat of this one test after staging verifies the final index, including the new shell test. Git whitespace checks passed. No complete backend/browser/smoke suite, production build, dependency installation or Docker startup was repeated. Earlier full-suite totals remain historical evidence, not new passes.

Local checks needed no outside-sandbox runtime execution. Updating the Git index and creating the authorized local commit may require sandbox approval for `.git` writes; that is unrelated to Docker or application access. Historical outside-sandbox commands remain documented above.

### P12E-CI-001 — initial login navigation timeout (OPEN)

This is a local tracked issue identifier, not a newly created GitHub issue.

- **Evidence:** latest complete two-worker Playwright execution: 15/16 passed, one error, zero retries/skips, 272.331 seconds. The administration scenario timed out at its initial `page.goto('/login')`, waiting for `load` for 20 seconds, before registration interaction. Earlier focused failures included initial Keycloak navigation and a safe BFF unavailable response; they remain separate recorded failures.
- **Scope:** browser/CI navigation reliability. The cause of the initial load timeout is not established. The invitation lost-click defect has a demonstrated hydration cause and three consecutive focused passes after its fix; these are different failures. Neither manual registration success nor recovered disk headroom proves this issue resolved.
- **Current mitigation:** CI uses one browser worker conservatively, with zero retries. This is not evidence of a green complete serial run or stable parallel execution.
- **Next bounded investigation:** preserve prior evidence and fixtures; capture sanitized page URL, navigation/redirect sequence, request completion, page snapshot, BFF/Keycloak diagnostics and cookie/session metadata at the initial timeout. Do not capture tokens or invitation links, weaken assertions or add arbitrary sleeps/retries. Reuse running infrastructure only where fixture isolation can be preserved.
- **Closure gate:** one complete 16-test browser execution with zero retries/skips, followed by aggregate Mailpit verification, and passing GitHub CI on the reviewed revision before merge approval. Do not combine partial runs. The existing independent 51-message Mailpit pass remains valid evidence, but does not close the browser gate.

No broad rerun is pending in this checkpoint. Continue development from the local commit while treating this issue and the documented support/security, target-architecture and real deployment prerequisites as outstanding. SMTP ambiguous acceptance still permits duplicate physical delivery; local verification does not establish exactly-once email or offsite disaster recovery.

## GitHub backend failure investigation — 21 October 2026

Investigated [CI run 39513130921, backend job 118938773590](https://github.com/wearthewo/sap-timesheet-automation/actions/runs/39513130921/job/118938773590), triggered for PR 32 on head `0f9a0c42ee27b0b81a798ada71352f23d02af242`. Actions checked out the synthetic merge `af7d9b4dfb4d2edd78812b3768af9e1ef24a0066`. GitHub blob hashes for the failing test and `ProjectService` match the local checkpoint, ruling out different implementations of these files in the tested merge.

Downloaded the actual `backend-reports` artifact and job log into ignored `.verification/phase12e/github-39513130921/` and `.verification/phase12e/github-backend-39513130921.log`. Summing all 17 Surefire XML reports confirms **409 tests: 408 passed, 1 failure, 0 errors, 0 skipped**. The only failing class/method is `com.acme.workforce.PhaseOneIntegrationTest.assignedProjectsRespectOwnerDatesAndActiveFlags`; that class ran 21 tests with one failure. All other classes passed. Production packaging was skipped after the failure.

The artifact's complete failure stack is:

```text
java.lang.AssertionError: JSON path "$.totalElements" expected:<1> but was:<2>
    at org.springframework.test.util.AssertionErrors.fail(AssertionErrors.java:61)
    at org.springframework.test.util.AssertionErrors.assertEquals(AssertionErrors.java:128)
    at org.springframework.test.util.JsonPathExpectationsHelper.assertValue(JsonPathExpectationsHelper.java:172)
    at org.springframework.test.web.servlet.result.JsonPathResultMatchers.lambda$value$2(JsonPathResultMatchers.java:112)
    at org.springframework.test.web.servlet.MockMvc$1.andExpect(MockMvc.java:214)
    at com.acme.workforce.PhaseOneIntegrationTest.assignedProjectsRespectOwnerDatesAndActiveFlags(PhaseOneIntegrationTest.java:170)
    at java.base/java.lang.reflect.Method.invoke(Method.java:580)
    at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
    at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
```

**Confirmed cause:** this legacy test constructed assignment dates using `LocalDate.now(ZoneOffset.UTC)`, while the production query correctly uses its injected clock in `Europe/Athens`. The failed HTTP response contains projects P0 and P2. Its fixture created P2 with `validFrom=2026-10-21` at approximately `2026-10-20T23:11:48Z`, when Athens was already October 21 (02:11). P2 was therefore valid on the business date, despite the test calling it a future assignment. This is a time-dependent fixture defect, not evidence of broken tenant filtering, PostgreSQL startup or Testcontainers.

| Environment | Earlier successful local run | Failed GitHub run |
| --- | --- | --- |
| Relevant execution time | October 18, approximately 15:55 UTC; UTC/Athens share October 18 | October 20, 23:11 UTC; Athens is October 21 |
| JVM default timezone | `Etc/UTC` | `Etc/UTC` |
| Java | Temurin 21.0.10 | Temurin 21.0.12.1 |
| Host/kernel | Docker Desktop, WSL2 Linux 6.6.87.2, amd64 | Ubuntu 24.04.5, Azure kernel 6.17.0-1022, amd64; runner 2.338.0, image 20261012.334.1 |
| Docker / Testcontainers / PostgreSQL | 29.3.1 / 1.21.4 / 17.11 | 28.0.4 / 1.21.4 / 17.11 |
| Surefire JVM | `-Xmx1024m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1` | `-Xmx1536m -XX:ActiveProcessorCount=2`, default context cache |

These environment differences are recorded, not presumed causal. The response payload and explicit calendar calculation explain the assertion failure without an OOM, connection or container-startup hypothesis.

**Smallest correction:** change only the affected test class to inject a fixed `notificationClock`, using the existing Mockito clock pattern. Parameterize the existing owner/date/active-state test at `2026-10-20T15:55:00Z` (expected Athens date October 20) and `2026-10-20T23:11:48Z` (expected October 21). Fixture dates use the explicit expected business date. All existing result, ownership and active-state assertions remain unchanged. Production code, migrations, dependencies, timeouts and workflow test selection are unchanged. Existing PhaseTwelveSecurityTest cases retain month-end, leap-year and DST coverage.

The workflow already has `if: always()` on report preservation and artifact upload. Both steps succeeded in this failed run; no speculative workflow change is needed. Reports are retained for seven days. Raw artifacts remain ignored and are not committed.

Read-only GitHub commands: `gh run list --repo wearthewo/sap-timesheet-automation --branch feature/phase12-production-readiness --limit 8 --json databaseId,workflowName,headSha,status,conclusion,createdAt,url`; `gh run view 39513130921 --repo wearthewo/sap-timesheet-automation --json jobs,headSha,event,createdAt,updatedAt`; `gh run download 39513130921 --repo wearthewo/sap-timesheet-automation --name backend-reports --dir .verification/phase12e/github-39513130921/backend-reports`; `gh run view 39513130921 --repo wearthewo/sap-timesheet-automation --job 118938773590 --log`; and GitHub REST commit/contents reads for the merge/file hashes. Sandbox credentials could not access the repository; outside-sandbox GitHub CLI reads succeeded. No GitHub run was restarted and no remote state was changed.

Focused verification uses the pinned Java 21 Maven image already documented above, a read-only backend source mount copied into ephemeral `/work`, the existing Maven dependency cache and disposable PostgreSQL Testcontainers. No application database/realm volume is mounted. It uses CI's Maven/Surefire memory and processor settings, without the earlier local context-cache override:

```sh
MAVEN_OPTS=-Xmx768m
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2 -XX:MaxRAMPercentage=50'
./mvnw -B -ntp test -Dtest=PhaseOneIntegrationTest,PhaseTwelveSecurityTest '-DargLine=-Xmx1536m -XX:ActiveProcessorCount=2'
```

The first focused execution ran **34 tests: 32 passed, zero assertion failures, two errors, zero skips**, in 3:03. Both errors were in the new parameterized case: the initial helper used `when(clock.withZone(any())).thenAnswer(...)` twice, causing the previous answer to evaluate Mockito's null matcher argument during re-stubbing. The exception was `NullPointerException: Cannot invoke "java.time.ZoneId.equals(Object)" because "zone" is null`, at `PhaseOneIntegrationTest.at:61` and `assignedProjectsRespectOwnerDatesAndActiveFlags:171`. Replacing that stub with `doAnswer(...).when(clock).withZone(any())` avoids evaluating the old answer during setup. This introduced test-helper error is distinct from the original GitHub assertion failure. Its full evidence remains in `github-39513130921/focused/`; the justified rerun writes to `focused-corrected/`, without overwriting the failed execution.

**Corrected focused execution: PASS, 34/34 tests, zero failures/errors/skips, Maven exit 0, 2:50 total.** PhaseOneIntegrationTest passed 22 cases (47.32 seconds), including both fixed-clock assignment cases; PhaseTwelveSecurityTest passed 12 cases (80.77 seconds), including the existing eight calendar/DST scenarios. Surefire XML/text, Java version, command script and exit code are preserved in `github-39513130921/focused-corrected/`. This local container used Temurin 21.0.10 and PostgreSQL 17.11 with the CI JVM settings; it is not an execution on GitHub's Java 21.0.12.1 runner. The first local run and corrected run are not combined into a larger pass count.

`git diff --check` passed. At the end of this diagnostic step, three files remained modified and uncommitted: `backend/src/test/java/com/acme/workforce/PhaseOneIntegrationTest.java`, this report and `docs/phase12-progress.md`. The test image was absent after disk cleanup and was pulled by pinned digest; no application image was rebuilt. Read-only GitHub operations and the two isolated test executions required outside-sandbox access. No complete backend/browser suite was rerun, no assertion was removed or relaxed, and no test was skipped. The original failed GitHub run remains historical evidence. No commit, push, merge, deployment or application-stack recreation was performed during diagnosis.

Subsequent review authorizes a local commit of these three reviewed files and a push to the existing `feature/phase12-production-readiness` branch, followed by GitHub CI/E2E inspection. The fixed instants and explicit Athens dates are deterministic, and the existing production business timezone and all assertions are preserved. No private verification artifacts are included. New remote-run outcomes must be read from GitHub; the earlier 34-test local pass alone does not establish a green complete CI or close the separate browser gate. No local full-suite run, application-stack recreation, merge or deployment is authorized.

### Phase 13B.1 dependency maintenance checkpoint - 24 October 2026

This addendum preserves all Phase12 evidence above. Candidate changes on `feature/phase12-production-readiness` update Node to24.22.1 with immutable production image pins and Spring Boot to4.1.2/springdoc3.1.1, after a written compatibility assessment. Java21 and existing business rules, schemas, worker scheduling and persisted data are unchanged. Production Node images omit unused npm; a same-version Keycloak26.8.1 rebuild fixes the OS CA package advisory.

Focused backend evidence: 22/22 initial tests; a 242-test run had one Jackson test-accessor error; the corrected 29-test class passed29/29. This is **264 unique passing tests across focused executions**, not a full suite. Packaging, frontend frozen install/lint/typecheck/production build pass. Smoke28/28, Mock SAP7/7, and infrastructure/CI tests20/20 pass (27 unique Node tests including Mock SAP). Initial smoke harness omitted NEXTAUTH_URL (24/28); corrected environment passed without weakening assertions. Maven137 runtime coordinates and both frontend dependency audits report zero advisories. Container findings remain; audits are not a universal clearance.

No new authenticated Playwright/full GitHub run is claimed. The prior green CI cannot accept this major framework upgrade. **Production-security gate remains blocked** by Caddy high findings, outstanding JDK/OS applicability review and full candidate CI. New AMD64 frontend/Mock SAP/Keycloak image builds passed; ARM64 was not reverified. All17 preexisting containers remained healthy with unchanged uptimes; only new disposable verification containers were removed. No database/realm changes, push, merge, deployment or checkpoint commit.

Exact versions, commands, failures/reruns, vulnerability counts/classification, image IDs, file inventory, disk usage and outside-sandbox operations: [Phase13B.1 dependency security](phase13b1-dependency-security.md). Next action is review, then explicitly authorized local checkpoint and later GitHub CI; resolve public-launch findings before deployment.