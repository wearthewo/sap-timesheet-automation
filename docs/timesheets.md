# Employee timesheets (Phase 2)

## Using the UI

Sign in, open **Timesheets**, choose a month, and select **Create draft** if it has not been started. A missing month is not created by viewing it. Select a work date first: the project dropdown contains active assignments valid on that date. Enter decimal hours and an optional description, then **Save entry**. Each save persists the draft; there is no separate whole-month save button.

Entries are grouped by date with daily totals. The monthly total stays visible above the editor. Edit or delete entries until ready, then select **Submit month** and **Confirm submission**. Submitted months remain in the paginated month list and open read-only with their submitted timestamp. Approval will be introduced in Phase 3; Phase 2 has no approval or return-to-draft operation.

## API

All paths below are under /api/v1. Ownership comes exclusively from the JWT subject's application user and employee record. No employee ID is accepted. An employee profile is required (403 EMPLOYEE_REQUIRED); inactive employees may read their history but cannot write. Other employees' IDs return 404 without revealing existence.

| Method | Path | Result |
|---|---|---|
| GET | /timesheets?page=0&size=12 | Own summaries, newest year/month first; size 1–100 |
| GET | /timesheets/{year}/{month} | Full month, or 404 TIMESHEET_NOT_FOUND; no side effects |
| POST | /timesheets | Create DRAFT with {year, month}; 201 with Location |
| POST | /timesheets/{id}/entries | Add entry; 201 with updated month |
| PUT | /timesheets/{id}/entries/{entryId} | Replace entry fields; 200 with updated month |
| DELETE | /timesheets/{id}/entries/{entryId} | Delete entry; 200 with updated month |
| POST | /timesheets/{id}/submit | Atomic final validation and submission; 200 |
| GET | /timesheets/{id}/available-projects?workDate=2026-10-05 | Eligible projects for the owned month's specified date |

Every entry mutation and submission requires **X-Timesheet-Version**, a nonnegative integer from the last month response. Missing/invalid headers return 400. The header travels from the Server Action to Spring Boot, alongside the server-held bearer token. The browser does not receive access tokens or call Spring Boot directly. Server Actions use Next.js origin protections, fixed backend routes, and the existing session mechanism; they contain no timesheet business rules.

Entry fields: projectId (UUID), workDate (ISO date), hours (decimal, at most two fractional digits), description (optional, at most 2000 characters). Hours can be supplied as a JSON decimal string or number; responses use decimal strings to avoid browser floating-point arithmetic. Daily/monthly totals are returned by the backend. Project metadata is loaded in batches, not through per-entry requests.

Errors retain timestamp/status/code/message/path. Relevant codes include TIMESHEET_NOT_FOUND (404), TIMESHEET_ALREADY_EXISTS (409), TIMESHEET_ALREADY_SUBMITTED (409), TIMESHEET_EMPTY (409), INVALID_WORK_DATE (400), PROJECT_NOT_ASSIGNED (400), PROJECT_ASSIGNMENT_INACTIVE (400), PROJECT_INACTIVE (400), DAILY_HOURS_EXCEEDED (400), and CONCURRENT_MODIFICATION (409).

## Persistence and concurrency

V5 creates timesheets, including a unique employee/year/month constraint, state/submittedAt consistency check, and optimistic version. V6 creates entries, positive numeric hours, foreign keys, and date/project lookup indexes. Both entities extend TimestampedEntity unchanged and use UUID foreign-key fields. Previous migrations are unchanged; Hibernate still validates only.

Years 1–9999 and months 1–12 are accepted. Entry dates must be within the sheet's month. There is no entry-per-project/day uniqueness restriction: multiple entries are allowed and all count toward the daily limit. Assignment start/end dates are inclusive, and both project and assignment must be active. Submission revalidates every entry against the current assignment/project state. Deletion can remove an entry that has since become invalid.

MAX_DAILY_HOURS defaults to 24, accepts a positive decimal with up to two decimal places (maximum 99999.99), and is configured on Spring Boot. The limit applies across all projects on each work date, not to individual entries alone. No eight-hour restriction exists. UTC determines the UI's current month/date; work dates themselves are timezone-free calendar dates.

**totalHours is persisted**, allowing efficient paginated month summaries. Every add/edit/delete recalculates daily limits and the monthly sum inside the same transaction as the entry change. Submission recalculates before setting SUBMITTED and submittedAt. The backend is the only application writer; direct operator edits to timesheets/entries would bypass these invariants and are unsupported.

Timesheet is the concurrency aggregate. Every mutation dirties the parent, even description-only edits with unchanged hours, so its JPA @Version advances. The client version rejects stale tabs early, and the database version predicate catches simultaneous writes. Losing transactions roll back all entry and total changes and return 409; the UI offers reload instead of silently overwriting. TimesheetEntry has no separate version because every entry operation participates in the parent transaction/version. Read-only detail transactions use a repeatable-read snapshot so parent version and entry totals describe the same committed state.

Submitted sheets have no employee write transition. Historical reads do not reapply a later daily-limit setting or assignment change. Project names/codes in views reflect the current project catalog; Phase 2 does not snapshot project names.

## Repeatable browser verification

The Playwright suite uses real Keycloak, BFF, Spring Boot and PostgreSQL; it does not mock authentication or business responses. Use a dedicated local employee and an unstarted month with two valid project assignments. The test submits that month permanently. Set TEST_USERNAME, TEST_PASSWORD, TEST_MONTH (YYYY-MM), TEST_PROJECT_ONE and TEST_PROJECT_TWO (project codes). Optional TEST_WORK_DATE defaults to the fifth day; BROWSER_BASE_URL defaults to http://localhost:3000.

With Node 24 and pnpm:
```sh
pnpm exec playwright install chromium
pnpm test:browser
```

The two browser cases cover desktop creation/add/edit/delete, two-project totals, a stale-tab conflict, submission/read-only history, mobile layout/navigation and both desktop/mobile logout. Traces are disabled to avoid retaining credentials/tokens. Screenshots are saved only after authentication, under ignored test-results/.

A scoped Node 24/Chromium runtime is available in frontend/tests/Dockerfile. Build from the frontend directory: docker build -f tests/Dockerfile -t workforce-browser-tests . Pass test variables with docker run -e NAME (values inherited from your shell). On Docker Desktop, BROWSER_HOST_MAP=1 maps Chromium's localhost to host.docker.internal while preserving the public OIDC issuer/origin. Follow the [Playwright Docker guidance](https://playwright.dev/docs/docker) for browser dependencies and container operation.

Manual onboarding remains unchanged. For a local fixture, create a dedicated Keycloak user, log in once, insert an employee tied to its verified subject, then create two projects and assignments covering TEST_WORK_DATE. No production seed or admin UI was added.
