# Phase 3 verification — 2026-10-06

Phase 3 implements manager approval/rejection only. No Phase 4, finance/SAP processing, workers, notifications, payroll or deployment was added.

## Final results

| Command/check | Result |
|---|---|
| ./mvnw -B -ntp test | 72 passed; 0 failures, 0 errors, 0 skipped |
| ./mvnw -B -ntp clean package -DskipTests | Passed |
| pnpm install --frozen-lockfile | Passed; no dependency/toolchain changes |
| pnpm lint | Passed, no warnings/errors |
| pnpm typecheck | Passed |
| pnpm build | Passed, including the final corrected Compose frontend image |
| pnpm test:smoke | 11 passed, 0 failures/skips |
| pnpm test:browser | 3 passed, 0 failures/skips on final run |
| docker compose config --quiet | Passed |
| docker compose up -d --build --wait | Passed; all four services healthy |
| Final frontend rebuild/start with --wait | Passed |
| GET /actuator/health | UP |
| GET /v3/api-docs | All five new operations present; DRAFT/PENDING_APPROVAL/APPROVED/REJECTED enum verified |
| GET /swagger-ui/index.html | HTTP 200 |
| git diff --check | Passed; Windows line-ending conversion notices only |

Backend totals: 21 Phase 1 + 29 Phase 2 + 22 Phase 3. Existing suites remain, with Phase 2 status assertions updated for the required lifecycle change. All database tests use real PostgreSQL Testcontainers; no H2, skipped tests or live Keycloak dependency for JUnit.

The Phase 3 suite verifies correct/unrelated managers, role restrictions including ADMIN/FINANCE, active/missing manager profiles, anonymous access, self-review protection, optional approval comments, required rejection comments, history creation, approved immutability, stale versions, explicit reopening/edit/resubmission, repeated decision history, concurrent decision rollback, PostgreSQL history immutability, and a real V6 SUBMITTED → V8 migration.

## Browser verification

Real Keycloak logins and real Next.js → Spring Boot → PostgreSQL calls, without mocked business responses:

1. Employee logged in; manager navigation was absent.
2. Created October 2026, selected October 5 and entered 6.50 hours.
3. Submitted to PENDING_APPROVAL and verified editing controls disappeared.
4. Manager logged in, saw the dashboard pending count and direct-report queue, opened the full entry detail.
5. At 375 × 812, manager navigation/logout were visible and there was no horizontal overflow.
6. Empty rejection comment failed native validation; whitespace-only comment was rejected by Spring Boot with a meaningful message.
7. Manager rejected with “Please correct the hours to 7.25.”; decision history appeared.
8. Employee logged in, saw REJECTED plus the reason/timestamp, explicitly reopened to DRAFT, edited to 7.25, and resubmitted.
9. Manager reopened the review and approved with a comment.
10. Employee saw APPROVED, 7.25 hours, both immutable decision records, and no editing/reopen controls.
11. Desktop/mobile logout through Keycloak succeeded throughout.

The two retained entry-flow cases also passed on a separate November fixture month, covering two-project daily/month totals, edit/delete, stale-tab conflicts, pending read-only state and mobile logout. The final suite ran three cases successfully.

Screenshots under ignored frontend/test-results/ include manager-rejected-mobile.png, manager-approved-desktop.png, employee-approved-desktop.png and the retained entry-flow screenshots. The mobile manager and approved employee screenshots were visually inspected.

## Issues found and fixed

- The initial self-review test tried to insert a self-manager relationship that Phase 1 already forbids with ck_employee_manager. The database constraint was preserved; the corrected test asserts that rejection as well as API self-review denial. The first run's single fixture error is not counted as a pass.
- Browser verification exposed a concrete Phase 2 UI defect: the editor key changed from year/month to database ID after draft creation. Server-action revalidation could remount the editor and reset a quickly selected work date to the month's default date. The key now stays stable for the year/month. Regression assertions check the saved work date, and all browser cases passed after rebuilding.
- The first browser run completed the approval scenario but failed the retained entry-flow date assertion and skipped its dependent mobile case. The final rerun used fresh dedicated fixtures and passed all three cases. Existing decision history was preserved; no audit rows were deleted to reset tests.

## Outside-sandbox runtimes and commands

Runtime verification used the established approved outside-sandbox Docker approach because the host toolchain does not provide the required Java 21 / Node 24 / pnpm setup and normal sandbox Docker access is restricted.

- Backend: maven:3.9-eclipse-temurin-21, repository Maven Wrapper, project Maven-cache volume, PostgreSQL Testcontainers through the Docker socket. Source was copied into the container filesystem to avoid Windows bind-mount compilation overhead; Surefire reports were copied back to backend/target/surefire-reports. JAVA_TOOL_OPTIONS=-Dapi.version=1.44 and TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal preserve the existing Docker Desktop configuration.
- Frontend: the existing workforce-browser-tests image provides Node 24, pnpm 12.8.1 and Chromium. Project node_modules/.next volumes were reused for install/lint/typecheck/build. Compose also built the production frontend with node:24-bookworm-slim.
- Browser: pnpm test:browser ran in the existing Node 24/Chromium image with current tests mounted read-only. BROWSER_HOST_MAP=1 preserved localhost OIDC URLs while routing Chromium to Docker Desktop's host gateway.
- Compose build/start/config checks, local HTTP checks, and dedicated Keycloak/PostgreSQL fixture setup also ran outside the sandbox.
- No global Java/Node/pnpm installation or unrelated machine configuration was modified. No Render commands were run.

Required commands executed: ./mvnw test; ./mvnw clean package -DskipTests; pnpm install --frozen-lockfile; pnpm lint; pnpm typecheck; pnpm build; pnpm test:smoke; pnpm test:browser; docker compose config --quiet; docker compose up -d --build --wait. A final frontend rebuild used docker compose up -d --build --wait frontend. Maven used only additional batch/no-transfer-progress flags.

## Local state and deliberate limits

Local Compose services remain running at http://localhost:3000. Verification users/credentials exist only in local Keycloak and ignored .verification files. Test employee/manager relationships, assignments, drafts and decisions exist only in the local PostgreSQL volume; no production seed was added.

A fresh fixture was used for the final browser rerun. The initial fixture and its approval history were retained. The final employee has an approved October month and a pending November month. Re-running the full suite requires a fresh dedicated fixture or other unstarted months; see approvals.md for environment variables.

Only MANAGER has approval capability, with an active employee profile and current direct-report relationship. There is no ADMIN/FINANCE override, indirect-report access or self-review. Employee onboarding and manager assignment remain trusted operator tasks. Employees without an assigned manager may submit, but no manager queue includes them until the relationship is configured.

Existing V1–V6 migrations and TimestampedEntity are unchanged. V7 migrates submissions and invalidates old versions; V8 adds append-only decisions. All mutations retain optimistic locking and atomic status/history updates. Phase 4 has not begun.
