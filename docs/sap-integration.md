# SAP integration boundary

Phase 5 keeps the Phase 4 SapTimesheetGateway boundary, HTTP adapter, export record and mock SAP, and moves execution into a durable PostgreSQL-backed worker. See [worker design](sap-export-worker.md) for the lifecycle, claims, retry policy, reconciliation and transaction details. [Phase 4 verification](phase4-verification.md) remains a historical record of the synchronous version.

## Adapter and contract

ApprovedTimesheetPayload is an immutable business model containing timesheet ID, employee number, year/month and entries with project code, ISO work date, BigDecimal hours and optional description. A durable snapshot is made at enqueue. JPA entities are never sent to SAP.

Only HttpSapTimesheetGateway maps those values to SAP-shaped ExternalSystemReference, PersonnelNumber, CalendarYear, CalendarMonth and TimeEntries (ProjectCode, WorkDate, Hours, Description). Hours are decimal strings. These vendor names, bearer credentials and protocol handling remain in infrastructure; timesheet and approval domain code remain independent of SAP transport.

The gateway exposes export(payload) and findByExportIdentity(identity). The latter returns an optional verified reference, not an HTTP response. RestClient uses the JDK request factory, bounded connect/response timeouts, no redirects, stable Idempotency-Key and correlation headers. Safe application failure categories cross the boundary; raw upstream bodies never do.

This mock-facing contract is not a certified production S/4HANA/OData endpoint. A future real adapter must preserve the gateway's idempotency and authoritative lookup guarantees without spreading SAP-specific logic throughout application modules.

## Authorization and UI

Only application database ADMIN/FINANCE can queue exports, retry eligible failures or reconcile UNKNOWN outcomes. Only APPROVED timesheets can initially enqueue. Manager approval remains separate and never automatically queues an export.

The SAP exports worklist displays status, attempts, last operation and next scheduled work. Detail pages show entries, totals, reference, safe failures and recent attempt history. Employee owners and direct-report managers retain read-only export status/history. Next.js forwards authenticated commands and polls read-only state; Spring remains authoritative.

Commands return 202 promptly:
- POST /api/v1/sap/timesheets/{id}/export (X-Timesheet-Version required)
- POST /api/v1/sap/timesheets/{id}/export/retry
- POST /api/v1/sap/timesheets/{id}/export/reconcile

Existing GET worklist, detail and export-state endpoints remain. GET /api/v1/sap/timesheets/{id}/export/attempts adds paginated history. API errors retain timestamp/status/code/message/path. Worker failures are persisted and shown through state reads, rather than returned as synchronous SAP 502/504 command responses.

## Identity, ambiguity and transactions

The identity is always SAP_TIMESHEET:<timesheetId>. One export and one job represent that logical operation across all attempts. Repeated enqueue returns existing state and never resets the job. SUCCEEDED is never exported again.

Reservation/job creation, claiming, and result persistence use separate short transactions. No transaction spans HTTP. A lost response may mean SAP accepted the document; UNKNOWN records therefore undergo identity lookup before another export. Lease recovery follows the same rule. A lookup finds the existing reference, establishes definite absence before a bounded retry, or leaves the result UNKNOWN. Attempt history is append-only.

## Local mock and configuration

mock-sap is a dependency-free Node 24 HTTP service in Compose with persistent accepted records and a health check. It is a local external-system simulator, not another application microservice. Backend health/startup does not require SAP availability. Authentication uses an independently generated development SAP_API_TOKEN from ignored .env.

- POST /sap/timesheets accepts the existing SAP-shaped body and matching Idempotency-Key.
- GET /sap/timesheets/{externalReference} retrieves an accepted document.
- GET /sap/timesheets/by-identity?identity=... resolves stable identity.
- GET /health is public.

Normal success returns 201 with success=true and externalReference. Invalid data/project returns 400; invalid credentials 401/403; duplicate identity 409; simulated unavailable service 500/503. Timeout controls distinguish acceptance before timeout from no creation before timeout. See [worker scenarios](sap-export-worker.md#local-scenarios-and-verification).

SAP_BASE_URL defaults to http://mock-sap:8090 in Compose. SAP_API_TOKEN is server-only. SAP_CONNECT_TIMEOUT defaults to 2s and SAP_RESPONSE_TIMEOUT to 5s. See worker documentation for all queue/lease/backoff variables. Do not expose any SAP or OIDC secret through NEXT_PUBLIC_ variables.

Run docker compose config --quiet and docker compose up --build. Existing manual employee/project onboarding remains. No production seeds or new admin onboarding UI were added. Preserve PostgreSQL and mock volumes across normal restarts.
