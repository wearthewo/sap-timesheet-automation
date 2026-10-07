# Finance reporting — Phase 6

## Scope and authorization

FINANCE and ADMIN database roles can read the operational dashboard and download reports. Every Spring service entry point checks the current application user's stored role. EMPLOYEE and MANAGER receive 403, including file endpoints. The BFF hides navigation as a convenience; it cannot grant access. No employee profile is required for FINANCE/ADMIN.

Reports calculate hours, not money. Rates, salary, overtime pay, payroll, payments, scheduled reports and notifications are outside this phase.

## Reporting semantics

The operational employee population is **employees.active = true at request time**, for every selected month. There are no employment-effective dates in the existing schema. Consequently a currently active employee can appear as missing in an older month, and a currently inactive employee is excluded even if they have historical hours. This is an explicit MVP limitation, not inferred employment history.

A left join to the selected month's timesheet retains employees with no row. ReportingTimesheetState derives NO_TIMESHEET with 0.00 hours and null timesheet/submission IDs; no fake timesheet is written. Other states are DRAFT, PENDING_APPROVAL, REJECTED and APPROVED.

ReportingSapState is a separate dimension:
- Non-approved or absent timesheets: NOT_APPLICABLE.
- Approved without an export record: NOT_EXPORTED.
- Otherwise the local export's QUEUED, PROCESSING, SUCCEEDED, FAILED or UNKNOWN state.

These derived states exist only in reporting. TimesheetStatus and SapExportStatus are unchanged.

The operational summary includes recorded hours from every lifecycle state and separate approved/exported totals. Its active employee count is the count matching the current filters; without filters it is organization-wide. SAP exception counts apply to approved timesheets only.

The latest approval decision is selected by decided_at DESC, id DESC. A later approval after rejection/reopening therefore replaces the old rejection in the current projection, without changing immutable history. Approved detail reads this same latest decision; the authoritative approval workflow ensures the latest decision is the one that established APPROVED.

## Approved versus SAP-exported hours

Project totals, employee/project breakdowns and CSV/detail rows always require timesheets.status = APPROVED.

- mode=APPROVED (default): no SAP success requirement.
- mode=SAP_EXPORTED: additionally requires sap_timesheet_exports.status = SUCCEEDED.

Existing entries on inactive projects remain included. Current project assignments and assignment validity are not rechecked during reporting: the timesheet workflow validated those when recording/submitting the hours. Joining assignments would incorrectly remove historical hours or multiply entries.

Only active employees are included in both modes. PostgreSQL numeric aggregation and Java BigDecimal preserve exact decimal hours. JSON hour fields are decimal strings, matching the existing timesheet API; Next.js displays backend totals without recomputing them.

## APIs and filters

All routes below begin with /api/v1/reports/monthly and require year=1..9999 and month=1..12:

| GET route | Response |
| --- | --- |
| /summary | MonthlyFinanceSummary |
| /employees | Paginated EmployeeMonthlyReportRow |
| /projects | Paginated ProjectMonthlyTotal |
| /employee-projects | Paginated EmployeeProjectHours |
| /approved-hours.csv | UTF-8 CSV attachment |
| /approved-hours.xlsx | Real XLSX attachment |

Filters on all routes:
- employee: case-insensitive literal substring of employee number, display name or email.
- project: case-insensitive literal substring of project code or name.
- timesheetStatus: a reporting timesheet state.
- sapStatus: a reporting SAP state, including NOT_APPLICABLE.
- mode: APPROVED or SAP_EXPORTED, applying only to approved-hours projections/download details.

Search text is trimmed, limited to 100 characters and passed as bound SQL parameters. Percent/underscore are literal characters, not user-controlled SQL wildcards.

For operational summary/employee rows, a project filter selects employees with an entry on a matching project in that month, and their **whole-timesheet total** remains visible. Missing timesheets do not match a project filter. For project/detail/breakdown rows, only entries on matching projects are summed. The UI labels this distinction.

Summary and Employees remain operational context in either hours mode. Choosing SAP_EXPORTED does not hide missing drafts from that context. Apply sapStatus=SUCCEEDED as well if the operational view should also be limited to successful exports. Contradictory state filters simply produce empty approved details.

Pagination defaults to 25, with page >= 0 and size 1..100; invalid values return the existing 400 validation error. Stable employee-number/UUID and project-code/UUID ordering prevents ambiguous page order. Employee/project totals have their own pagination. Downloads use all matching rows, not the current page.

## Query strategy and indexes

ReportingQueries uses NamedParameterJdbcTemplate read projections. A common CTE left-joins active employees, app_users, manager profiles, the month timesheet, latest indexed approval and the unique SAP export. Grouped SQL aggregates entries without loading JPA graphs. Page count/data queries and bounded download queries have a constant query count, independent of employee count; there is no repository-per-employee loop.

The lateral latest-decision lookup uses the existing (timesheet_id, decided_at, id) index. Timesheet lookup uses the unique (employee_id, year, month) index; entries use the existing timesheet-leading index; exports have a unique timesheet index. Current project assignments are deliberately not joined.

No reporting table, copied operational data, materialized view or V12 migration is needed. Existing indexes cover these joins; local query-plan inspection did not demonstrate a need for another index. Reassess with representative production volume rather than adding speculative indexes.

## Snapshots and transactions

ReportingService uses read-only REPEATABLE_READ transactions. Each API response has a consistent local PostgreSQL snapshot, including page count/data. Downloads buffer all five bounded projections in one transaction and close it before CSV/XLSX formatting in ReportDownloadService. Reports never request FOR UPDATE or change operational state.

The UI's summary and three lists are separate API requests; each is internally consistent, but they can straddle a concurrent update. A single workbook is consistent across its sheets. No distributed SAP snapshot is taken. Export state is the last durable local state and can change immediately after generation.

## CSV and Excel

CSV contains detailed approved entry rows with employee number/name/email/manager, period, project code/name, work date, exact decimal hours, description, timesheet status, submitted timestamp, latest approval decision/timestamp, SAP state and reference. It quotes every field, doubles embedded quotes and preserves commas/newlines using CRLF record delimiters and UTF-8. Formula-like text is prefixed with an apostrophe to prevent spreadsheet formula execution; numeric hour values remain unchanged.

[Apache POI](https://poi.apache.org/download.html) poi-ooxml 5.5.1 creates a genuine Office Open XML workbook with:
1. Summary: operational counts/totals plus selected filters and mode.
2. Employees: operational employee rows, including missing timesheets.
3. Project Totals: approved hours or SAP-exported approved hours.
4. Employee Project Hours: the same hours-mode breakdown.
5. Approved Hour Details: the same detailed rows as CSV.

The first two sheets provide clearly labeled operational context, **not a claim that draft hours are approved**. Only the final three sheets contain the finance-safe approved-hours dataset.

Headers are bold, frozen and filterable. Columns have fixed reasonable widths; there is no expensive row-by-row auto-sizing. Hours use numeric cells with 0.00 formatting. Exact decimal values are written directly to OOXML numeric values without converting BigDecimal to double. Text is always a string cell, never a formula. Dates/times use native Excel cells and UTC display; dates before Excel's 1900 epoch remain ISO text. Excel itself has its normal 15-significant-digit numeric precision limit.

Response filenames:
- workforce-approved-hours-YYYY-MM.csv / .xlsx
- workforce-sap-exported-hours-YYYY-MM.csv / .xlsx

Content types are text/csv;charset=UTF-8 and application/vnd.openxmlformats-officedocument.spreadsheetml.sheet. The Next.js Node route /api/reports/{csv|xlsx} forwards only supported filter parameters, attaches the server-held access token, and streams the backend response while preserving filename/content type. Both layers send no-store. No token reaches browser JavaScript. Errors retain the standard JSON ApiError response even when Accept requests only CSV or XLSX, so authorization/validation/size failures keep their 403/400/413 status.

## Limits and configuration

REPORT_MAX_DOWNLOAD_ROWS defaults to 10000, permitted configuration range 1..50000. It caps each workbook sheet/dataset, including operational employees; approved detail reads at most limit + 1 before rejecting. Exceeding a limit returns 413 REPORT_TOO_LARGE with guidance to narrow filters. CSV uses the same snapshot/limit rules for consistency. This bounds buffered report models and workbook memory at MVP scale. In-memory generation remains subject to ordinary service concurrency/capacity limits; bulk scheduled exports are not implemented.

Logs contain user ID, period, report type, mode, format, row count and duration. They do not contain tokens, complete report rows, search text, descriptions or credentials.

## UI and local verification

FINANCE/ADMIN choose Reports in navigation. Select year/month, apply URL-backed employee/project/status filters, inspect operational exceptions, choose the hours mode and download CSV/Excel. The employee table scrolls within its panel on small screens; page navigation and logout remain accessible.

Optional local fixture (not automatic or a migration):

```powershell
Get-Content infra/dev/reporting-fixtures.sql | docker compose exec -T postgres psql -U workforce -d workforce -v report_prefix=REPORT6-example
```

Use a new unique prefix for each run. The existing local manager profile is used if available. Fixtures contain synthetic application users, no passwords, seven employees (the requested six lifecycle examples plus a permanent SAP failure), two projects and October 2028 hours. Filter employee by the chosen prefix. Expected active count: 7; approved count: 3; approved hours: 14.00; SAP-exported hours: 8.25. SAP reference rows are synthetic reporting fixtures, not calls to production or mock SAP.

Browser tests require the existing Phase 5 role/workflow credentials and fresh SAP fixtures, plus TEST_REPORT_PREFIX for this dataset. Existing Phase 1–5 tests remain unchanged. See [Phase 6 verification](phase6-verification.md) for executed commands and results.
