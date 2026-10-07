# Phase 6 verification

Verified locally on 2026-10-07. Phase 6 is complete. No deployment or Phase 7 work was performed.

## Final results

| Check | Result |
| --- | --- |
| Full Maven Wrapper suite, Java 21 / Testcontainers PostgreSQL | **182 passed**, zero failures, errors or skips |
| Phase 1 / 2 / 3 / 4 / 5 / 6 suites | 21 / 29 / 22 / 28 / 33 / 49 passed |
| Maven clean package -DskipTests | Passed after the full test run |
| pnpm install --frozen-lockfile | Passed; existing lockfile retained |
| pnpm lint | Passed |
| pnpm typecheck | Passed |
| pnpm build | Passed; final production Compose frontend build also passed |
| pnpm test:smoke | **16 passed** |
| Playwright | **8 workflows verified**: six existing workflows passed in the full run; both reporting workflows passed in the final focused rerun |
| Mock SAP Node HTTP suite | **7 passed** |
| docker compose config --quiet | Passed |
| docker compose up -d --build --wait | Passed |
| PostgreSQL / Keycloak / backend / frontend / mock-sap | All healthy |
| Backend /actuator/health | UP |
| /v3/api-docs | All six reporting paths present |
| /swagger-ui/index.html | HTTP 200 |
| git diff --check | Passed |

The first full browser run passed all six existing workflows and exposed issues in the two new reporting tests. After the fixes below, both reporting workflows passed against the final stack (50.9 seconds). Existing timesheet, approval and SAP browser tests were not weakened or modified. Backend verification was rerun in full after the shared JSON error-handler fix.

## Backend coverage

The 49 PhaseSixIntegrationTest cases use PostgreSQL Testcontainers and Spring Security mock JWTs. They verify:

- Database FINANCE/ADMIN authorization, EMPLOYEE/MANAGER rejection and anonymous rejection.
- Active employees without timesheets, zero hours, null IDs/managers, and no fake timesheet creation.
- Exact lifecycle and SAP counts across all states, including NOT_APPLICABLE and NOT_EXPORTED.
- Exact recorded, approved and SAP-exported decimal totals.
- Employee identity/manager data and latest approval replacing an earlier rejection.
- Pagination, invalid parameters, literal case-insensitive employee/email/project filters and status filters.
- Approved-only project and employee/project aggregation, including inactive historical projects.
- Distinct APPROVED versus SAP_EXPORTED details.
- CSV UTF-8, commas, quotes, newlines, decimal values, formula neutralization and filenames.
- Real XLSX opened with Apache POI: five sheets, row counts, headers, numeric hours, date cells, formatting, freeze panes and empty detail handling.
- Configured row-limit rejection and no database transaction during formatting.
- Explicit CSV/XLSX Accept headers preserving JSON 403, 400 and 413 errors.

The final full run was 182 tests with zero failures/errors/skips, followed by a successful Java package build. The existing suites remain intact.

## Browser and downloaded-file verification

A fresh local fixture set preserved existing operational/audit data. Seven reporting employees cover approved/exported, approved/not-exported, pending approval, rejected, draft, no timesheet and an additional permanent SAP failure. The selected month is October 2028, scoped by its unique employee prefix.

Verified through real Keycloak, Next.js BFF, Spring Boot, PostgreSQL and mock SAP:

1. FINANCE navigation and reporting page.
2. Month selection and switching, URL-backed employee/project/timesheet/SAP filters, including SAP failure.
3. All seven employees, including NO_TIMESHEET.
4. Exact summary values: 7 employees, 3 approved, 20.25 recorded hours, **14.00 approved hours**, **8.25 SAP-exported hours**.
5. Approved project totals: REPORT-A 10.75 and REPORT-B 3.25; four employee/project groups.
6. CSV download filename, escaped content, approved-only rows and SAP-exported exclusion.
7. XLSX download filename and valid ZIP signature. The downloaded archive was also opened independently: Summary, Employees, Project Totals, Employee Project Hours and Approved Hour Details were present, with 7 employee rows, 2 project rows, 4 breakdown rows and 4 approved detail rows. Backend POI tests validate cell types, dates and values.
8. ADMIN access, mobile layout without page overflow and logout.
9. EMPLOYEE/MANAGER hidden navigation, access-denied UI and **403** on both download formats.
10. Existing employee entry, submission, rejection/reopening/approval, SAP success/failure, automatic retry, UNKNOWN reconciliation, manual retry and mobile/logout regressions.

Desktop and mobile report screenshots were visually reviewed. The employee table scrolls inside its panel rather than overflowing the page. Controls, summary cards, project breakdowns, downloads and logout remain accessible. Ignored screenshots/downloads are under frontend/test-results.

## Query plans and persistence

No V12 migration was required; V1-V11 were not changed. Reporting creates no persistent tables or materialized views.

Local EXPLAIN (ANALYZE, BUFFERS) for the employee/timesheet/latest-approval/SAP join showed an index scan backward on ix_approvals_timesheet, with small-table hash/sequence scans elsewhere and approximately 1.2 ms execution for the local fixture set. Existing unique employee/year/month, entry-timesheet and export-timesheet indexes cover the joins. This is local plan inspection, not a production-scale performance benchmark.

## Issues found and fixed

- **Real application defect:** file requests with an Accept header for CSV or XLSX could turn a DomainException into HTTP 500 because the shared error response had no explicit media type. ExceptionHandlerAdvice now explicitly returns application/json for ApiError. Forbidden, invalid and oversized file requests retain 403/400/413, with regression tests. Existing JSON API conventions are unchanged.
- Added explicit accessible names to the new select controls so month/status/mode controls are identified correctly.
- Scoped the reporting access-denied browser assertion to main content, avoiding Next.js's separate route-announcement alert.
- Used browser-origin fetch for download authorization tests; Playwright's Node HTTP client does not inherit Chromium's Docker localhost mapping.
- Corrected new test expectations for MockMvc URL encoding and workbook summary row position.
- Aligned report decimal JSON serialization with existing string-based timesheet DTOs.

No changes to the timesheet lifecycle, approval behavior, SAP identity/retries/reconciliation or existing tests were needed.

## Commands and outside-sandbox runtimes

All runtime/build/browser/Docker/local-service verification ran **outside the normal sandbox**, using project-scoped Docker runtimes. No global Java, Node or pnpm installation was changed.

Backend used maven:3.9-eclipse-temurin-21, the repository Maven Wrapper, the existing Maven cache volume, a container-local source copy, Docker socket access for Testcontainers, TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal and JAVA_TOOL_OPTIONS=-Dapi.version=1.44:

```sh
./mvnw -B -ntp compile
./mvnw -B -ntp -Dtest=PhaseSixIntegrationTest test
./mvnw -B -ntp test
./mvnw -B -ntp clean package -DskipTests
```

Frontend used the existing Node 24 / pnpm 12.8.1 / Chromium verification image:

```sh
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm build
pnpm test:smoke
pnpm test:browser
pnpm test:browser reports.spec.ts
```

Other verification:

```sh
node --test server.test.mjs  # existing mock-sap tests, Node 24 container
docker compose config --quiet
docker compose up -d --build --wait
docker compose build frontend
docker compose up -d --no-deps --wait frontend
docker compose ps
git diff --check
```

Local HTTP checks covered health, OpenAPI and Swagger. Existing ignored fixture scripts provisioned local Keycloak users; infra/dev/reporting-fixtures.sql added explicitly synthetic local reporting data. No real credentials were committed. Source edits, archive inspection and static Git checks used the normal workspace.

## Limits

Active employment is the current employee.active flag, not historical employment dates. Separate UI API requests can observe slightly different snapshots; a workbook uses one repeatable-read snapshot. SAP status is local durable state, not a live SAP query. Downloads have a configurable per-dataset row cap (default 10000); large-scale streaming/scheduled reporting is not included. Reports calculate hours only, with no salary, payroll, payments, notifications, production SAP connection or deployment.
