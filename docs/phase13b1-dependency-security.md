# Phase 13B.1 — production dependency security

## Compatibility assessment before implementation (2026-10-23)

Starting revision: `692636d`, branch `feature/phase12-production-readiness`, clean working tree. Existing containers, databases, realms and volumes must remain unchanged. Verification uses scoped runtimes and disposable test resources; no deployment, push or commit is authorized in this checkpoint.

Node 24.18.0 predates the [21 October security release](https://nodejs.org/en/blog/vulnerability/october-2026-security-releases). Select Node **24.22.1**, retaining the Node 24 major. The official `node:24.22.1-bookworm-slim` index was resolved through Docker to `sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205`; it contains AMD64 and ARM64 manifests. Manifest support is not ARM runtime verification. CI reads the root `.nvmrc`; development, browser-test and production Dockerfiles must agree. pnpm stays 12.8.1. The frontend lockfile contains package versions, not the Node executable; a runtime patch alone does not justify dependency churn.

Spring Boot **4.1.2** is the candidate maintained line. Its [system requirements](https://docs.spring.io/spring-boot/system-requirements.html) support Java 21 and Maven Wrapper 3.9.11. Boot 3.5.16 has ended OSS maintenance. No paid support or preview release is selected.

| Area | Existing | Proposed compatibility work / risk |
| --- | --- | --- |
| Framework / Security / ORM / servlet container | Boot 3.5 BOM plus overrides | Use Boot 4.1.2 BOM; remove obsolete Tomcat 10/Jackson 2 overrides. Verify Hibernate schema validation and authorization with PostgreSQL integration tests. |
| MVC / resource server / Flyway | Older starter layout and direct Flyway | Use Boot 4 modular starters so auto-configuration remains present. Preserve settings, migrations and request policies. |
| OpenAPI | springdoc 2.8.17 | springdoc 3.1.1 for Boot 4.1; verify generated contract and Swagger resources. |
| JSON | Jackson 2 injected mapper and exception imports | Migrate to Jackson 3, retain annotation namespace and enable Boot's Jackson 2-compatible defaults. Test decimal/date JSON and historical export snapshot readability. No change to business payload fields. |
| Tests | Boot 3 MockMvc package; Testcontainers 1 coordinates | Adapt Boot 4 test modules/imports and BOM-managed Testcontainers coordinates. Preserve every test and assertion. |
| POI / JDBC / logging | Explicit security overrides | Inspect resolved BOM versions and advisory results before retaining or removing overrides. |
| Workers | Existing scheduler, claims, retry policies | No schedule, concurrency, lease, SQL or business-rule changes. |

References: [Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide), [Boot 4.1 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes), [springdoc compatibility](https://springdoc.org/).

Decision: attempt compilation and focused compatibility verification before accepting the migration. Complete GitHub CI cannot verify unpushed changes; the preceding green run is baseline evidence only. Results and remaining blockers will be recorded below before review.

## Implementation and verification, 23-24 October 2026

**Status: dependency update implemented for review; security clearance remains blocked.** No checkpoint commit, push, merge or deployment was made. The full GitHub regression must run after a separately authorized push; the earlier green 409-test run is not evidence for this candidate. No assertion was removed or test skipped to make the compatibility run pass.

### Resolved versions and scope

| Component | Candidate |
| --- | --- |
| Node, all repository Dockerfiles / `.nvmrc` | 24.22.1, official multiarch index pinned above |
| Frontend engine / package manager | `>=24.22.1 <25` / pnpm 12.8.1 |
| Next / React / NextAuth | Unchanged lockfile: 16.4.1 / 19.3.0 / 4.24.15 |
| Java / Maven Wrapper | Java 21 / 3.9.11, unchanged |
| Spring Boot / Framework / Security | 4.1.2 / 7.0.10 / 7.1.2 |
| Hibernate / Jackson databind | 7.4.13.Final / 3.1.7 (Jackson 2.21.7 also remains as a managed transitive dependency) |
| Flyway / Tomcat / PostgreSQL JDBC | 12.4.0 / 11.0.27 / 42.7.14 |
| springdoc / POI / Angus Mail | 3.1.1 / 5.5.1 / 2.0.5 |
| Log4j / commons-lang3 / Testcontainers | 2.25.5 / 3.20.0 / 2.0.5 |
| Production Keycloak | Same 26.8.1, rebuilt index `sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b` |

The Boot BOM replaces the obsolete Boot 3 overrides. Modular MVC, RestClient, resource-server, Flyway and MVC-test starters preserve auto-configuration. Jackson imports and Testcontainers coordinates follow their maintained APIs. `spring.jackson.use-jackson2-defaults=true` preserves previous serialization defaults. Angus is explicitly compile-scoped because existing SMTP classification uses its exception types. Business rules, SQL, V1-V17 migrations, worker timing/concurrency, authentication policies and frontend lockfile are unchanged.

Production frontend and Mock SAP images remove bundled npm after building; neither serving process needs it. This removes its vulnerable dependency tree rather than suppressing findings. Development/build images still contain npm to install pnpm. CI reads `.nvmrc` already, so no workflow edits were needed.

### Commands and observed results

Scoped Windows Node was verified against the official release SHA256 before extraction. `pnpm` is the repository-local 12.8.1 executable. Java verification uses the pinned Maven/Java21 container from `images.lock.json`, source copied to ephemeral `/work`, Maven seed cache mounted read-only, and disposable PostgreSQL Testcontainers. No existing application database was connected to or migrated.

| Command / check | Observed result |
| --- | --- |
| `./mvnw -B -ntp test-compile` | Pass: 108 production and 18 test source files compile; initial module/import failures corrected below |
| `./mvnw -B -ntp test -Dtest=PhaseOneIntegrationTest` | 22/22 pass; Flyway V1-V17, Hibernate validation, JWT/role/API coverage |
| `./mvnw -B -ntp test -Dtest=PhaseTwoIntegrationTest,PhaseThreeIntegrationTest,PhaseFiveIntegrationTest,PhaseSevenIntegrationTest,PhaseEightSapDownloadTest,PhaseNineIsolationTest,PhaseElevenAdministrationTest,PhaseTwelveSecurityTest,SmtpEmailGatewayTest` | First run: 242 tests, 241 pass, one error, zero skipped; details below |
| `./mvnw -B -ntp test -Dtest=PhaseTwoIntegrationTest` after assertion correction | 29/29 pass, zero failures/errors/skips |
| `./mvnw -B -ntp clean package -DskipTests` | Pass, executable 86,933,263-byte JAR; packaging intentionally does not rerun tests |
| `./mvnw -B -ntp dependency:list -DincludeScope=runtime -DoutputFile=/evidence/runtime-dependencies.txt` | 137 resolved runtime coordinates audited through OSV batch API; zero reported advisories |
| `pnpm install --frozen-lockfile` | Pass; no lockfile change |
| `pnpm lint` / `pnpm typecheck` | Both pass |
| `pnpm build` in `docker build -f infra/production/frontend.Dockerfile ...` | Production build passes on patched Node24; no duplicate local build |
| `pnpm audit --prod --json` / `pnpm audit --json` | Zero vulnerabilities reported for runtime and complete frontend dependency graph, respectively |
| `node --test mock-sap/server.test.mjs infra/production/production.test.mjs infra/production/line-endings.test.mjs infra/ci/ci.test.mjs` | 27/27 pass: 7 Mock SAP, 11 production, 1 LF, 8 CI tests |
| `node --test infra/production/production.test.mjs infra/production/line-endings.test.mjs` after Keycloak pin | 12/12 pass; repeated subset, not additional unique tests |
| `node --test mock-sap/server.test.mjs` inside new production Mock SAP image | 7/7 pass; repeated contract suite, not additional unique tests |
| `node --test tests/smoke.test.mjs` with `SMOKE_BASE_URL` pointing to new disposable production frontend | 28/28 pass after fixing verification environment; first harness run had four failures (24/28) because required `NEXTAUTH_URL` was omitted |
| Full backend / authenticated Playwright / aggregate Mailpit | Not run for this candidate. Required in GitHub CI after authorization; no new six-service stack created |

Backend unique coverage is **264 tests passing across focused runs**, not one full-suite pass: PhaseOne 22, PhaseTwo 29, PhaseThree 22, PhaseFive 34, PhaseSeven 43, PhaseEightSapDownload 33, PhaseNine 33, PhaseEleven 26, PhaseTwelve 12 and SMTP 10. The failed 29-test class and its rerun are counted once. Test JVM used `-Xmx1024m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1`; helper Maven used Java21 with bounded memory. Full CI remains responsible for all other tests and authenticated cross-service compatibility.

Confirmed compatibility corrections:

- Boot 4 moved the MVC test package and `MockMvcBuilderCustomizer`; corrected imports. No security or business assertion changes.
- Boot 4 mail provider is runtime-scoped; existing compile-time SMTP exception handling required explicit managed Angus dependency. SMTP classification tests pass.
- `PhaseTwoIntegrationTest.createsDraftAndIndependentEmployeeMonth` called `decimalValue()` on the intentional JSON string `"0.00"`. Jackson 2 returned zero for a nonnumeric node, hiding an ineffective assertion; Jackson 3 throws `JsonNodeException`. The test now requires a string and parses it with BigDecimal before comparing to zero. API decimal strings are unchanged; nonzero decimal aggregate tests also pass.
- Added `historicalJacksonTwoSnapshotKeepsDatesDecimalsAndUnicode`: literal historical JSON, not JSON generated by the candidate mapper; 25/26 October calendar dates, 0.10 + 0.20 = 0.30, Greek text and null description, XLSX numeric total and no SAP call. Passed in the 33-test download class.

Verification harness issues retained in private evidence: initial Windows bind-mounted Maven resolution was stopped for I/O slowness before compilation and replaced with an ephemeral Linux working/cache copy; one shell-redirection wrapper returned nonzero after the underlying initial 22-test Maven run succeeded; local pnpm launcher paths were corrected; one Docker registry download returned EOF and a bounded retry succeeded. Trivy filesystem artifact-cache locking on a Windows bind mount was resolved with `--cache-backend memory`, retaining the downloaded vulnerability DB and every finding. Smoke tests correctly returned 503 without configured application origin; adding `NEXTAUTH_URL` to the disposable test container yielded 28/28. No application bypass, arbitrary test retry or changed expected status was introduced.

### Security scans and remaining risk

Scanner: `aquasec/trivy:0.76.0@sha256:2416d1f74045b57274df1a340eee6b510cacd73c66a6ccb8da092b117eb7d222`, fresh vulnerability databases, `--scanners vuln`, no severity filters, ignore file, `--ignore-unfixed` or suppression. 0.76.1 was advertised during the run; the recorded tool remains the immutable 0.76.0 release. Scan scope is Linux AMD64. Remote images were scanned by immutable index and new runtime images by immutable local ID. ARM64 manifests were inspected; new ARM64 builds/runtimes were not tested here.

Counts below are scanner **package/advisory occurrences**, not distinct exploitable bugs. Zero Maven/npm audit findings does not mean container or JDK security clearance.

| Scanned artifact | Critical | High | Medium | Low | Unknown |
| --- | ---: | ---: | ---: | ---: | ---: |
| Node24.22.1 pinned base, including npm | 1 | 60 | 95 | 84 | 1 |
| New production frontend, npm removed | 1 | 56 | 87 | 83 | 1 |
| New production Mock SAP, npm removed | 1 | 56 | 87 | 83 | 1 |
| Pinned Temurin21 JRE Jammy base | 0 | 0 | 27 | 16 | 0 |
| Pinned PostgreSQL17 Alpine base | 1 | 25 | 32 | 3 | 3 |
| Previous Keycloak26.8.1 pin | 2 | 1 | 55 | 35 | 0 |
| Selected rebuilt Keycloak26.8.1 pin | 1 | 1 | 55 | 35 | 0 |
| Current Caddy2.11.7 pin | 0 | 5 | 14 | 1 | 3 |
| Investigated rebuilt Caddy2.11.7 candidate, not selected | 0 | 5 | 13 | 1 | 1 |

- **Fixed runtime-tooling exposure:** removing npm removes four high, eight medium and one low finding from each Node runtime image. High findings were npm's brace-expansion, http-cache-semantics and undici dependencies. Production application package scans report no language-library vulnerabilities. Build/development npm remains tooling exposure and is not described as patched.
- **Fixed OS trust-store finding:** same-version rebuilt Keycloak removes CVE-2026-114406 in ca-certificates. The [Red Hat advisory](https://access.redhat.com/security/cve/cve-2026-114406) describes trust in compromised weak-key intermediate issuers. This is relevant to outbound TLS and warrants pinning the rebuilt release without changing the realm.
- **Open JDK trust-store review:** selected Keycloak still reports critical CVE-2026-91403 (`java-21-openjdk-headless`, status `under_investigation`) and high CVE-2026-91415. [Red Hat says non-portable OpenJDK RPMs consume the OS ca-certificates fix](https://access.redhat.com/security/cve/cve-2026-91403). Thus the critical occurrence may overlap the corrected OS store; it is not independently confirmed exploitable or silently waived. A network-isolated check of the rebuilt image resolves every installed Java `lib/security/cacerts` link to `/etc/pki/ca-trust/extracted/java/cacerts`; the scanned OS package is `2026.2.92_v9.0.319-90.3.el9_8`, the fixed release. This supports the vendor-stated OS-store mitigation for this particular Keycloak image. Retain the critical scanner occurrence and confirm vendor status before launch; no hostile-certificate handshake was attempted. The image omits the rpm CLI, so package version evidence comes from the scanner inventory. Java2D font processing relevance also needs reachability review. Temurin's 21.0.12.1+1 bundled JRE needs a separate Java advisory/trust-store assessment: its zero high/critical OS scan is not a complete Java binary audit.
- **Public-launch blocker: Caddy.** Current pin and current rebuilt tag both retain Go1.26.8 / x/net0.59.0 high findings: CVE-2026-111943, CVE-2026-78667, CVE-2026-78669 (two package occurrences) and CVE-2026-97031. HTTP/2/TLS are relevant to the public edge; do not infer unreachability from internal network isolation. [Go's HTTP/2 advisory](https://pkg.go.dev/vuln/GO-2026-6611) documents remotely induced CPU exhaustion. Require an official patched build (or separately reviewed reproducible rebuild and regression) before launch. Investigated index `sha256:eb4d35b7452d1ab6171fbdbe8d9275b2301dad6dad92115d91e19fb9b24d510f` did not fix the five high findings and was not substituted.
- **Node Debian OS:** critical CVE-2023-45853 is the zlib source-package/minizip case; [Debian marks its relevant binary unaffected](https://security-tracker.debian.org/tracker/CVE-2023-45853). Preserve the scanner finding and vendor qualification rather than claim an exploitable Node critical. High findings remain in Debian OS utilities/libraries. Nonroot/capability restrictions reduce some local escalation exposure, but no blanket waiver is granted. Resolve patches or document package-specific reachability before public launch.
- **PostgreSQL image:** all reported critical/high occurrences are in the embedded Go standard library of `/usr/local/bin/gosu`, not the PostgreSQL engine. Network/TLS Go advisories are not proof of PostgreSQL network exposure. Gosu's execution path must be checked and an updated official image considered; this audit does not mark all of them fixed or suppress them.
- Medium/low/unknown findings remain in full reports. Scan results are point-in-time and must be refreshed before deployment. Final assembled backend, Caddy and backup-helper images were not rebuilt/scanned in this checkpoint; runtime Maven coordinates and their pinned Java/PostgreSQL roots were assessed. No complete-image or ARM64 clearance is claimed.

Representative scan command (real image refs come from `images.lock.json`):

```sh
docker run --rm --memory=768m \
  -v "$EVIDENCE:/evidence" -v "$CACHE:/root/.cache/trivy" \
  aquasec/trivy:0.76.0@sha256:2416d1f74045b57274df1a340eee6b510cacd73c66a6ccb8da092b117eb7d222 \
  image --cache-backend memory --image-src remote --platform linux/amd64 \
  --timeout 5m --skip-db-update --scanners vuln --format json \
  --output /evidence/scan.json IMAGE@sha256:DIGEST
```

New local images use `--image-src docker` and their immutable IDs with a temporary scanner's Docker socket mount. Raw JSON, Maven XML, logs and runtime tools remain under ignored `.verification/phase13b1/`; they are not repository deliverables or production secrets. No credentials, database dump, application data or downloaded tool is staged.

### Review inventory and next gate

Changed paths: `.nvmrc`; `backend/pom.xml`; four production Jackson import/exception sites (SecurityConfiguration, HttpSapTimesheetGateway, SapExportSnapshotReader, SapJobStore); `application.yaml` Jackson defaults; fifteen backend test/fixture files for Boot/Testcontainers/Jackson compatibility and the snapshot regression; frontend package engine and development/test Dockerfiles; Mock SAP Dockerfile; production frontend/Mock SAP/Keycloak Dockerfiles, image lock, image resolver and production tests; README and three readiness/progress documents including this one. No migration, worker schedule, persisted data, frontend business component or lockfile changed.

Recommended next step: review this candidate and authorize a local checkpoint commit; only after separate push authorization run the complete GitHub backend and authenticated browser/Mailpit workflows. Resolve the public-edge/JDK/container findings before deployment. Do not interpret targeted compatibility passes as acceptance of a major framework migration or readiness to open public registration.
### Final local checkpoint evidence

AMD64 production builds passed for frontend, Mock SAP and Keycloak. Keycloak's network-isolated `kc.sh --version` reports 26.8.1 / Red Hat OpenJDK21.0.12.1; this is a build/version check, not an authenticated realm migration test. Existing realm and database services were not accessed by the new image. Immutable built IDs:

- Frontend: `sha256:da768ae94c917c9694bdfe63a7a11d824ceb8a3da0a0bda25d84ea79fe946977`.
- Mock SAP: `sha256:b94ff199419c5dd82cd825ea62872e304ae60103a1263a8a5665d24a296764a3`.
- Keycloak: `sha256:f9b754e73bc7f4d954ff7e9cb366f85df802193e439990e45622d2e576887fb7`.

All 17 preexisting containers remained running and healthy, with three-day uptimes at the final inspection. The temporary Java verification container was removed only after its reports and packaged JAR were copied to ignored evidence; disposable frontend test containers and scanner containers were removed. No existing volume was modified or removed, and no existing service was restarted or replaced. Host disk free space was approximately 54.66 GiB before verification and 48.13 GiB after builds/scans. Images and ignored evidence remain available for review; no pruning was performed.

Outside-sandbox access was required for Docker/Testcontainers, scoped runtime and dependency downloads, registry manifest/image access, vulnerability databases/API calls, and disposable localhost smoke execution. Global Java/Node installations and Git configuration were not changed. Read-only Git/source review and repository edits ran in the workspace.

Git branch remains `feature/phase12-production-readiness`, baseline HEAD `692636d`. All changes remain unstaged/uncommitted for review. `git diff --check` passes. `git check-ignore` confirms private verification reports, scoped runtimes, packaged JAR and scanner evidence are excluded. No push/merge/deployment or existing application refresh occurred.

**Decision:** the candidate is ready for code review and a separately authorized checkpoint/CI run; Phase 13B.1 is not a resolved production-security gate. The clear remaining blocker is Caddy's public-edge high findings, with JDK/OS package triage and complete candidate CI still required. Do not proceed to public deployment on the strength of these targeted results.
### Checkpoint review and authorized GitHub verification - 24 October 2026

Reviewed all 36 changed paths before staging. Production Java changes are limited to dependency API imports/exception types and Jackson compatibility defaults; there are no changed business SQL statements, DTO definitions, authorization policies, transaction boundaries, migration scripts, worker schedules or frontend business components. All existing test assertions remain, with the decimal-string assertion corrected and the historical snapshot regression added. Prior immutable-image runtime evidence confirms startup without npm (frontend 28/28 smoke and Mock SAP 7/7 contract tests); neither production entrypoint invokes npm.

The lightweight review reran `node --test mock-sap/server.test.mjs infra/production/production.test.mjs infra/production/line-endings.test.mjs infra/ci/ci.test.mjs`: 27/27 passed, zero failures/skips. `git diff --check` passed. All V1-V17 migration file objects match HEAD, and the frontend lockfile is unchanged. No secrets or generated verification/database artifacts were found in the reviewed changes; private evidence remains ignored. No local QA build, database operation or container replacement was performed during this review.

No blocking regression was found in the review or targeted evidence. The user has now authorized one checkpoint commit (`build: upgrade Node and Spring Boot dependencies`) and a push to the existing feature branch. PR #32 targets main; pushing updates its CI checks. Full-stack E2E is manually dispatched on the exact pushed feature revision because feature pushes do not trigger that workflow automatically. Only completed GitHub runs at this revision count as new full-regression evidence; their exact job results will be reported after execution. Earlier passing runs remain baseline evidence.

The existing Caddy public-edge high findings, Java trust-store/font applicability questions, Debian OS findings and PostgreSQL gosu review remain open as documented above. Approval to commit/push for CI is not production security clearance. No merge or deployment is authorized.

### Post-browser notification readiness correction — 24 October 2026

Checkpoint `a4dd3185dcb485932226d45d42629607347c4eb7` reached GitHub. [Core CI run 40064923117](https://github.com/wearthewo/sap-timesheet-automation/actions/runs/40064923117) passed: backend 411/411 (zero failures/errors/skips), production packaging, frontend install/lint/typecheck/build and 28 smoke tests, Mock SAP 7 tests/image build, and quality checks. This supersedes the pending-CI statement above; it does not resolve the documented production security findings.

[E2E run 40064934207, job 120786069689](https://github.com/wearthewo/sap-timesheet-automation/actions/runs/40064934207/job/120786069689) passed 28 smoke tests and 16 Playwright tests with one worker and zero retries, but **the complete E2E job failed**. The browser completion log is `05:14:43.1820409Z`; `CI test failed: Readiness deadline exceeded` follows at `05:15:44.1726502Z`. Aggregate Mailpit verification was never reached. All six services were healthy in the saved diagnostics.

**Confirmed gate defect:** `infra/ci/stack.mjs` waited up to 60 seconds for the count of fixture-user notifications with `status <> 'SENT'` to become zero. A terminal CANCELLED notification can never satisfy that predicate. Saved `diagnostics/jobs.txt` contains 65 SENT notifications and one `APPROVAL_REMINDER|CANCELLED|1`, with no failed or unresolved notification rows. Expected negative-test SAP failures are separate from notification delivery failures.

**Historical evidence limitation:** the old diagnostic query retained only notification type, status and attempt count. It did not retain the cancelled notification ID, business key, recipient/organization, period, related timesheets or immutable attempt reason. The disposable GitHub database was removed during cleanup. Those exact historical values cannot be recovered from the saved artifacts, and the legitimacy of this particular cancellation is **not independently proven**. Do not infer its timesheet or reason from row order or label it a confirmed valid cancellation.

The existing production rule in `NotificationStore.claim/relevant` cancels an approval digest when it is no longer relevant and appends `NOTIFICATION_NO_LONGER_REQUIRED` to its attempt history. An active manager with no remaining pending direct-report timesheets for that month is a legitimate example. `NotificationEvaluation.managers` uses the digest key `APPROVAL_REMINDER:<managerEmployeeId>:<year-month>:<Europe/Athens-date>`; it is a manager/month digest, not a one-timesheet notification. No application notification code or business rule was changed.

The verification correction accepts only a narrowly evidenced obsolete approval digest:

- Read notification and independent organization, membership, manager, same-organization direct-report timesheets, approval history and delivery-attempt evidence in one PostgreSQL statement.
- Require the exact manager/period/Athens-day business key, active organization and MANAGER membership/profile, no sent timestamp, and exactly one CANCELLED attempt with `NOTIFICATION_NO_LONGER_REQUIRED`.
- Require associated timesheets, no currently pending report, review decisions by that manager before cancellation, and at least one review between enqueue and cancellation. An older decision cannot justify a resubmission before cancellation. Later reopen/resubmit/approval cycles retain earlier immutable decision evidence.
- Unsupported or unproven cancellations and FAILED/unknown states fail immediately. PENDING/PROCESSING must resolve within the **unchanged 60-second deadline**. No error-swallowing retry is used for invalid evidence.
- Every SENT intent still requires exactly one Mailpit message matching recipient, subject and normalized UTF-8 plain-text body, with no HTML. Every previously required business notification type must still have SENT coverage. An accepted cancellation must have zero matching delivered messages. A truncated Mailpit search fails rather than silently accepting incomplete deduplication evidence.
- Capture `diagnostics/notification-readiness.json` before evaluation and during failure diagnostics. It contains IDs, key, period, states and safe attempt/decision evidence, excluding addresses, message subjects/bodies, invitation links and credentials. Full intents remain in the existing private verification file, outside uploaded diagnostics.

This deliberately fails closed for role-loss/inactive-profile cancellations, ambiguous prior SMTP attempts, absent historical evidence, or a business-day boundary not corroborated by the persisted timestamps. The worker currently captures the cancellation attempt time at claim transaction start; a concurrent decision later in that transaction window may be legitimate but will not pass the strict historical-time proof. Such a failure requires inspection of the new evidence, not a timeout increase or blanket cancellation exemption. SMTP ambiguous-acceptance duplicate-delivery limitations remain unchanged.

Focused verification used the repository-scoped Node **24.22.1**, without rebuilding or recreating the local QA stack:

| Command/check | Observed result |
| --- | --- |
| `node --check infra/ci/notification-verification.mjs` and `node --check infra/dev/verify-phase8-mailpit.mjs` | PASS |
| `node --test infra/ci/ci.test.mjs` | 48/48 PASS: 40 new focused cases plus 8 existing CI cases |
| `node --test infra/ci/ci.test.mjs infra/production/production.test.mjs infra/production/line-endings.test.mjs` | 60/60 PASS, zero failures/skips; includes the same 48 tests, not 108 distinct tests |
| `node .verification/phase13b1/notification-sql-check.mjs` | First run exposed a JSON aggregate alias collision; corrected `decision` row alias to `review`. Rerun: 4/4 PostgreSQL checks PASS |
| PostgreSQL query checks | Actual V1–V17 schema: valid cancellation with other-tenant pending report excluded; escaped subject scope; pending direct report rejected; later resubmission invalidates older approval |
| `git diff --check` | PASS |

The PostgreSQL query exercise applied unchanged migration SQL to a new, network-isolated PostgreSQL 17 container with tmpfs data, no host ports or mounted existing volumes. V15's placeholder was substituted with `false` only in the in-memory SQL for this empty database. This was a query/schema compatibility check, **not** a Flyway-runner or backend integration-suite rerun. Both temporary containers were removed in `finally`; existing databases, volumes and QA services were untouched. Docker access for this focused exercise required outside-sandbox execution. Local Node checks required no global toolchain change. The temporary query script and downloaded historical evidence stay ignored under `.verification/phase13b1/`.

Changed repository files: `infra/ci/stack.mjs`, `infra/ci/ci.test.mjs`, new `infra/ci/notification-verification.mjs`, new `infra/ci/notification-verification.test.mjs`, `infra/dev/verify-phase8-mailpit.mjs`, and this report. The existing quality workflow imports and runs the new cases through its unchanged CI test entrypoint. No backend, migration, dependency, scheduler, timeout or workflow configuration changed.

**Current status:** focused correction verified locally, uncommitted on `feature/phase12-production-readiness` for review. No new full browser run or live aggregate Mailpit run was performed; the Mailpit regression cases use deterministic HTTP responses. GitHub E2E acceptance remains pending a separately authorized commit/push/run. The historical cancellation cannot be retroactively certified. No production security clearance, merge or deployment is claimed.
