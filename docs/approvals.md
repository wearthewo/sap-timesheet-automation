# Manager approvals — Phase 3

The workflow uses the existing Java 21 modular monolith, PostgreSQL/Flyway persistence and Node 24 Next.js BFF. No finance, SAP, workers or notifications are implemented.

## Lifecycle and migration

```text
DRAFT --submit--> PENDING_APPROVAL --approve--> APPROVED
                         |
                       reject
                         v
                      REJECTED --reopen--> DRAFT
```

DRAFT is employee-editable. PENDING_APPROVAL and APPROVED are read-only. REJECTED is also read-only until its owner explicitly selects **Reopen draft**. Reopening clears submittedAt, increments the timesheet version, retains entries/totals and preserves all decision history. The employee can then edit and resubmit. Resubmission performs the same non-empty, project/assignment/date and daily-total checks as before and sets a new submittedAt.

An approved timesheet cannot be reopened, edited or receive another decision through these APIs. Rejection alone does not secretly change status on the next entry mutation. Only a pending timesheet can receive a manager decision; invalid transitions return HTTP 409.

V7 replaces the old state constraint and converts every Phase 2 SUBMITTED row to PENDING_APPROVAL, preserving submittedAt, entries and totals. It increments version and updates updatedAt to invalidate old browser versions. No approval decision is fabricated for migrated data. Existing DRAFT rows are unchanged. V1–V6 are unchanged; Hibernate still validates only.

V8 creates timesheet_approvals with UUID foreign keys, enum/comment constraints and timesheet/manager indexes. A PostgreSQL trigger prevents updating or deleting decision rows; the JPA entity is immutable as well. Database owners can still perform administrative maintenance, but application code must never bypass append-only history. Tests truncate isolated fixture databases only.

## Authorization

Spring Boot resolves the application user from the authenticated Keycloak subject and reads the role from the application database. Manager endpoints require **MANAGER**, an existing active employee profile, and the relationship:

```text
timesheet.employeeId -> Employee.managerId == current manager Employee.id
```

Only direct reports qualify. Self-review is forbidden even if a relationship were misconfigured; the existing employee database constraint also forbids self-manager links. EMPLOYEE, FINANCE and ADMIN do not receive manager privileges in this phase. There is no frontend-supplied manager ID and no ADMIN override.

The pending queue is filtered by this relationship and excludes the manager's own sheets. Detail/history and decisions recheck the current relationship. A reassigned employee becomes visible to the new manager, and the old manager loses access. History keeps the deciding manager's employee UUID permanently. Current direct-report details/history remain readable after decisions, including during a reopened draft.

Employees access only their own timesheets through existing employee endpoints. Manager access never enables self-service operations against another employee. Employees without a manager can still submit; their pending sheet becomes reviewable once a trusted operator assigns a manager. No auto-approval or fabricated manager is introduced.

## API and errors

All calls require authentication. Lists are paginated (page >= 0, size 1–100, default 12), ordered by submittedAt then ID, oldest first.

| Method | Path | Purpose |
|---|---|---|
| GET | /api/v1/approvals/pending | Current manager's pending direct-report summaries and employee names/numbers |
| GET | /api/v1/approvals/timesheets/{id} | Authorized employee details, entries, totals, status and decision history |
| POST | /api/v1/approvals/timesheets/{id}/approve | Optional body/comment; record approval |
| POST | /api/v1/approvals/timesheets/{id}/reject | Required nonblank comment; record rejection |
| POST | /api/v1/timesheets/{id}/reopen | Owner explicitly reopens a rejected month |

Approve/reject bodies use { "comment": "..." }, limited to 2000 characters. Approval accepts no body or an optional comment. Rejection rejects omitted, empty or whitespace-only comments with REJECTION_COMMENT_REQUIRED (400).

Every approve/reject/reopen request requires X-Timesheet-Version from the current detail DTO. Both manager and employee responses include the updated timesheet version. A manager review response is { employee, timesheet }. The existing employee Detail now includes approvalHistory in chronological order; each row contains id, managerEmployeeId, decision, comment and decidedAt. The latest rejection is shown prominently when status is REJECTED, while all prior decisions remain visible after reopen/resubmission/approval.

Errors retain timestamp/status/code/message/path. Codes include APPROVAL_NOT_AUTHORIZED (403), MANAGER_EMPLOYEE_PROFILE_NOT_FOUND (403), TIMESHEET_NOT_FOUND (404), TIMESHEET_NOT_PENDING_APPROVAL (409), TIMESHEET_ALREADY_APPROVED (409), TIMESHEET_NOT_REJECTED (409), REJECTION_COMMENT_REQUIRED (400), and CONCURRENT_MODIFICATION (409). Existing entry-mutation errors and validation conventions remain supported.

OpenAPI at /v3/api-docs derives these operations from controllers/DTOs. No duplicate TypeScript API schema or generated client has been added.

## Transactions and optimistic locking

Each decision validates the manager relationship and client version, checks the pending state, appends one immutable decision, and updates the parent status in one transaction. JPA @Version on Timesheet ensures simultaneous decisions cannot both commit. The losing transaction rolls back its audit insert and returns 409. Stale tabs are rejected before writing. Reopening also checks and advances the parent version.

Approval does not alter entries or totals; submission remains responsible for their final validation. Persisted decimal totals and the original aggregate version strategy are unchanged. Read detail transactions use consistent snapshots. Project metadata, employee names and queue summaries are loaded without per-entry/per-row queries.

## UI and local verification

Managers see **Approvals** in the mobile-friendly navigation and a pending count on their dashboard. Open a queue item to inspect the employee, month, entries and daily/monthly totals. Approve or reject requires an explicit confirmation; rejection requires a comment. After a decision, the same page displays history and no decision controls.

Employees see PENDING_APPROVAL, APPROVED or REJECTED. A rejected month shows the latest reason/time and **Reopen draft**. Decision history survives every cycle. All business validation remains in Spring Boot; Server Actions propagate server-held bearer tokens and version headers.

Manager onboarding remains a trusted operator operation: set the verified application user's role to MANAGER, create their employee record if absent, and set the reporting employee's manager_id to that employee UUID. Neither JWT role claims nor browser payloads grant authority. No production seeds or admin onboarding UI were added.

The browser suite now requires TEST_MANAGER_USERNAME, TEST_MANAGER_PASSWORD and TEST_APPROVAL_MONTH (an unstarted YYYY-MM), in addition to the employee/project variables described in timesheets.md. Use a dedicated employee directly assigned to this manager and two valid project assignments. Keep TEST_MONTH (the existing entry-flow scenario) and TEST_APPROVAL_MONTH different. The full approval scenario submits, rejects with required-comment validation, reopens/edits/resubmits, approves, checks immutable history/read-only UI, and verifies mobile navigation/logout. Test credentials stay in ignored local files or environment variables.

Run pnpm test:browser against the live Compose stack with Node 24, or use the existing frontend/tests/Dockerfile runtime. No live Keycloak is needed for JUnit tests; those use mock JWTs and real PostgreSQL Testcontainers.
