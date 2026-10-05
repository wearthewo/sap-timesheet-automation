# Phase 2 verification — 2026-10-05

Phase 2 is complete. No Phase 3 work or deployment was performed. The Phase 1 architecture, migrations V1–V4, TimestampedEntity and existing backend tests are preserved.

## Results

| Check | Result |
|---|---|
| Java 21 ./mvnw test | 50 passed: 21 unchanged Phase 1 + 29 Phase 2; zero failures/errors/skips |
| ./mvnw clean package -DskipTests | Passed |
| pnpm install --frozen-lockfile | Passed |
| pnpm lint | Passed, no warnings/errors |
| pnpm typecheck | Passed |
| pnpm build | Passed in Node 24 verification and Compose production builds |
| pnpm test:smoke | 9 passed; zero failures/skips |
| pnpm test:browser | 2 passed; zero failures/skips on the final run |
| docker compose config --quiet | Passed |
| docker compose up -d --build --wait | Passed; PostgreSQL, Keycloak, backend and frontend healthy |
| Backend /actuator/health | UP |
| /v3/api-docs | Successful; all 8 new operations on 6 timesheet paths present |
| /swagger-ui/index.html | HTTP 200 |
| git diff --check | Passed (Windows line-ending conversion notices only) |

The 29 new PostgreSQL Testcontainers cases cover month creation/duplicates/independent ownership, missing-month GET without creation, decimal totals, invalid precision/zero/negative hours, daily limits and rollback, date/assignment/project eligibility, edit/delete totals, ownership, immutable submission, empty submission, final revalidation, required client versions, concurrent API writes, and two controlled transactions reading the same version. A generated OpenAPI regression check verifies decimal string schemas. No H2 or skipped persistence tests.

## Browser evidence

Real Chromium → Next.js BFF → Spring Boot → PostgreSQL, with real Keycloak authorization-code login:

1. Logged in as a dedicated local EMPLOYEE and opened Timesheets.
2. Created October 2026 as DRAFT.
3. Added 6.25 and 2.50 hours on two projects; daily/monthly totals were 8.75.
4. Edited the first entry to 5.50; total became 8.00.
5. Deleted the second entry; total became 5.50.
6. A stale second browser tab tried to delete an entry and received a concurrency error without changing the saved sheet.
7. Added 1.25 hours again; total became 6.75.
8. Submitted the month and verified the submitted timestamp, read-only view, and absence of entry/edit/delete controls.
9. Returned to the list and saw SUBMITTED and 6.75 hours.
10. Logged out through Keycloak on desktop, logged in again at 375 × 812, opened the submitted month read-only, and logged out on mobile.

Mobile navigation/logout were visible and horizontal overflow checks passed. Draft mobile and submitted desktop/mobile screenshots were captured under ignored frontend/test-results/. Draft mobile and submitted desktop screenshots were visually inspected. Browser contexts closed automatically at test completion.

## Issues found and resolved

- Generated OpenAPI initially described BigDecimal response fields as numbers while Jackson correctly emitted strings. Explicit response-schema annotations now match the wire format; the regression test and live document both confirm string types.
- The prior Phase 1 fixture password was rejected by Keycloak. A fresh dedicated local Phase 2 account was created; the existing account's credentials were not changed. The failed login runs are not counted as successful verification.
- The browser harness initially located a nested project select by exact label text, which includes option text in Playwright label matching. It now uses the combobox's accessible role/name. The read-only assertion is specific to the submitted banner, and dependent browser scenarios run serially.
- An early health probe overlapped backend restart. Final verification waits for healthy services and successfully checks health/OpenAPI/Swagger.
- Windows PowerShell treated redirected native Docker progress output as an error status although the image-build log completed successfully. The final unredirected docker compose up -d --build --wait command returned success.

## Commands and scoped runtimes

All runtime/package/network/Docker/browser commands required outside-sandbox execution because this host has Node 22, no Java/pnpm on PATH, and sandbox Docker access restrictions established in Phase 1. Java 21 and Node 24 were retained in disposable Docker environments. No unrelated global installations or machine configuration changed.

Executed:
- Java 21 Maven-wrapper ./mvnw -B -ntp test, then ./mvnw -B -ntp clean package -DskipTests.
- Node 24 npm view @playwright/test version; pnpm add -D -E @playwright/test@1.63.0 to update the manifest/lockfile.
- pnpm install --frozen-lockfile; pnpm lint; pnpm typecheck; pnpm build; pnpm test:smoke.
- docker compose config --quiet; docker compose up -d --build; docker compose build backend; final docker compose up -d --build --wait; docker compose ps.
- docker build -f tests/Dockerfile -t workforce-browser-tests . from frontend, then pnpm test:browser in that Node 24/Chromium image.
- Live HTTP health/OpenAPI/Swagger requests and local fixture setup through Keycloak administration and PostgreSQL.
- Final pnpm lint and pnpm typecheck after refining the browser tests.

Backend verification copies sources into the container filesystem before compiling, uses the project Maven-cache volume, mounts the Docker socket only for Testcontainers, and copies Surefire reports into backend/target/surefire-reports. JAVA_TOOL_OPTIONS=-Dapi.version=1.44 and TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal preserve the working Docker Desktop test configuration. Frontend dependencies/build output use project-named Docker volumes.

The browser image is defined in frontend/tests/Dockerfile. BROWSER_HOST_MAP=1 maps localhost to Docker Desktop's host gateway while retaining the public OIDC origin. Credentials were supplied through temporary process environment variables and are stored only in ignored .verification/phase2-test-user.json. Tests have no credential/token traces. The final run mounted the current tests/browser source read-only into the cached test image.

See [timesheet documentation](timesheets.md) for browser prerequisites and [Phase 1 verification](verification.md) for scoped-runtime command examples.

## Deliberate limits and local state

The local Compose services remain running at http://localhost:3000. The local test database contains two verification employees, two projects, assignments and the Phase 2 employee's submitted October test month. No production seed was added. On a clean installation, Keycloak users and application employee/project onboarding still require the documented operator setup.

The browser test deliberately consumes an unstarted month for its dedicated test employee; use another fixture or unstarted assigned month on a later run. An empty draft from a failed harness run was removed only for that dedicated fixture before the successful rerun.

The lifecycle is DRAFT → SUBMITTED. Totals are persisted and recalculated transactionally. Every mutation requires X-Timesheet-Version; the parent @Version guards concurrent database writes. Submitted sheets are immutable through employee endpoints. Approval is reserved for Phase 3. No approval tables, SAP integration, workers, reporting, notifications, payroll or deployment were added.
