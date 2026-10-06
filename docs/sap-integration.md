# SAP integration — Phase 4

Phase 4 adds a synchronous manual export of APPROVED timesheets. It does not contact a production SAP system. The local HTTP contract is SAP-shaped, not a certified S/4HANA API or a complete OData implementation.

## Boundary and flow

```text
APPROVED timesheet -> manual export command -> SapTimesheetGateway
 -> HttpSapTimesheetGateway -> mock SAP HTTP locally / real SAP adapter later
```

The `sap` feature owns export policy, records and HTTP mapping. `ApprovedTimesheetPayload` contains immutable business values: timesheet ID, employee number, period, project codes, dates, decimal hours and optional descriptions. The gateway is the only required port. SAP field names, authentication headers and HTTP response interpretation stay in infrastructure; timesheet and approval domains have no transport dependency.

The adapter uses Spring RestClient with the JDK HTTP request factory, positive configurable connect/read timeouts, no redirects and no application retry loop. [Spring's request factory documentation](https://docs.spring.io/spring-framework/docs/6.1.8/javadoc-api/org/springframework/http/client/JdkClientHttpRequestFactory.html) describes the duration timeout API. Requests carry a bearer credential, Idempotency-Key and X-Correlation-ID (the timesheet UUID). Logs contain export/timesheet IDs, duration, outcome and validated external reference, never tokens, payloads or upstream error bodies.

## Authorization and API

Application database ADMIN and FINANCE roles can browse the approved export worklist, open details and issue export commands. No employee profile is needed for these administrative roles. MANAGER and EMPLOYEE cannot export, even if a timesheet is their own. Approval does not trigger export.

| Endpoint | Access / behavior |
| --- | --- |
| GET /api/v1/sap/timesheets?page=0&size=20 | ADMIN/FINANCE, paginated approved worklist, newest period first |
| GET /api/v1/sap/timesheets/{id} | ADMIN/FINANCE, employee, entries, totals, approval history and export state |
| POST /api/v1/sap/timesheets/{id}/export | ADMIN/FINANCE; requires X-Timesheet-Version; synchronous result |
| GET /api/v1/sap/timesheets/{id}/export | ADMIN/FINANCE, active owner or active direct-report MANAGER; 404 if no attempt |

Next.js adds SAP exports navigation for ADMIN/FINANCE. Open a record and choose Export to SAP. Successful records show the external reference and timestamp; failed/unknown records show safe messages and no retry button. Employee and manager approved views display state without export controls. Refresh export status is a read-only request, not a retry or polling job.

The BFF keeps credentials server-side and forwards the employee's access token to Spring. SAP credentials never enter the frontend. The export command's BFF timeout defaults to 20 seconds and must exceed the configured SAP connect plus response timeout with additional database allowance. A lost BFF response triggers only a state GET; it never repeats the command.

## Persistence, concurrency and transactions

V9 creates `sap_timesheet_exports`, extending TimestampedEntity conventions with a timesheet FK and unique timesheet ID. Exactly one logical record/attempt is allowed per timesheet in this phase. External references are unique when present. PostgreSQL checks ensure success has a reference/timestamp and failures have safe code/message fields. Statuses:

- PENDING: reservation committed; request may be in flight or process may have stopped.
- SUCCEEDED: confirmed acceptance and external reference stored.
- FAILED: definite validation/authentication rejection, or missing local configuration.
- UNKNOWN: ambiguous timeout, transport/server failure, duplicate upstream response or unverified success response.

The identity is always `SAP_TIMESHEET:<timesheetId>`. It is deterministically reconstructed from the durable FK, never regenerated per attempt. A succeeded export cannot run again; failed, unknown and pending attempts also cannot be retried in Phase 4. The unique constraint resolves concurrent reservations; the losing request returns 409 without making an HTTP call.

The command checks the client's timesheet version and APPROVED state. Export does not alter the timesheet or increment its version: approved content is already immutable through all existing APIs. Export uniqueness protects this separate operation. No unrelated optimistic-locking behavior is changed.

1. A REQUIRES_NEW transaction authorizes, loads approved content, builds the detached payload, reserves PENDING and commits.
2. The orchestrator suspends any ambient transaction and calls the gateway with no database transaction or lock open.
3. Another REQUIRES_NEW transaction changes the result and related reference/timestamp/failure fields together.
4. Only after failure persistence does the API return its safe error.

A process failure after reservation can leave PENDING indefinitely. A successful SAP write followed by response loss is UNKNOWN locally. A database failure while recording a confirmed result can leave PENDING despite acceptance. Neither state means it is safe to send again. Keep the same identity and reconcile the remote document before any future retry. There is deliberately no reset/delete/retry API. Automatic reconciliation, retry policy and background processing are deferred to Phase 5.

HTTP 400/422 maps to SAP_VALIDATION_ERROR; 401/403 to SAP_AUTHENTICATION_FAILED; 409 to SAP_DUPLICATE; 5xx to SAP_UNAVAILABLE; timeouts to SAP_TIMEOUT. Malformed or unsafe success references map to SAP_EXPORT_UNKNOWN. Error envelopes retain the existing timestamp/status/code/message/path convention. SAP rejection codes are application-level 502 responses (timeout 504), because the backend is acting as an integration gateway, while eligibility/duplicate local attempts return 409 and role failures 403. Upstream bodies are never returned.

## Mock HTTP service

`mock-sap/server.mjs` is a dependency-free Node 24 service in Compose, with persistent `mock_sap_data` storage. It is a development dependency, not a new application microservice or production SAP replacement. It is reachable only on the Compose network by default.

POST /sap/timesheets requires Bearer authentication and an Idempotency-Key matching ExternalSystemReference. Example body:

```json
{
  "ExternalSystemReference": "SAP_TIMESHEET:00000000-0000-0000-0000-000000000001",
  "PersonnelNumber": "E001",
  "CalendarYear": 2026,
  "CalendarMonth": 10,
  "TimeEntries": [
    {"ProjectCode": "PROJECT-A", "WorkDate": "2026-10-05", "Hours": "6.25", "Description": "Implementation"}
  ]
}
```

Decimal hours are strings; dates are ISO calendar dates. Optional descriptions remain ordinary JSON text. The mock validates required fields, period/date consistency, positive decimal hours and lengths. Accepted identities persist across restarts. Concurrent duplicates receive 409; GET /sap/timesheets/{externalReference} retrieves the accepted document with the same authentication. GET /health is public.

Normal valid projects succeed with 201 and `{ "success": true, "externalReference": "SAP-TS-...", "message": "Timesheet accepted" }`. Special project codes are intentionally reserved in this development mock:

| Project / request | Response |
| --- | --- |
| MOCK-INVALID | 400 INVALID_PROJECT |
| Wrong bearer token | 401 UNAUTHORIZED |
| MOCK-AUTH | 403 FORBIDDEN |
| Repeated identity | 409 DUPLICATE_TIMESHEET |
| MOCK-SERVER | 503 SERVICE_UNAVAILABLE |
| MOCK-TIMEOUT | Accept and persist, then delay response 10 seconds (longer than default adapter timeout) |

For an end-to-end failure exercise, create/assign a MOCK-INVALID project, record time, submit and approve normally, then export as FINANCE/ADMIN. These codes are mock behavior only; no production data or privileged browser scenario header is introduced. The deterministic backend HTTP stub additionally tests 500, redirects and malformed responses.

## Configuration and local operation

Generate an independent development-only SAP_API_TOKEN in your ignored .env. Do not reuse Keycloak or database secrets.

- SAP_BASE_URL: Compose defaults to http://mock-sap:8090; configurable backend destination.
- SAP_API_TOKEN: backend and mock authentication secret.
- SAP_CONNECT_TIMEOUT: positive Spring duration, default 2s.
- SAP_RESPONSE_TIMEOUT: positive Spring duration, default 5s.
- SAP_EXPORT_REQUEST_TIMEOUT_MS: BFF request bound, default 20000.

The backend can start without a configured/reachable SAP endpoint; it does not include SAP in readiness and does not depend on mock health. The mock has its own health check. Missing configuration is reported only on export. Other application functionality stays available during an SAP outage.

Run `docker compose config --quiet` then `docker compose up --build`. Never delete existing application or mock volumes merely to rerun a test. Create fresh approved fixtures instead. For the mock contract tests run `node --test mock-sap/server.test.mjs` using Node 24.

Browser tests require fresh employee/manager fixtures as before plus TEST_FINANCE_USERNAME/PASSWORD, TEST_ADMIN_USERNAME/PASSWORD, TEST_SAP_SUCCESS_ID and TEST_SAP_FAILURE_ID. The latter IDs refer to two approved employee-owned months (2027-01 normal project, 2027-02 MOCK-INVALID in the current browser fixture). Do not commit credentials. Trusted local database onboarding is preserved; there is no new admin UI or production seed.

No automatic export, workers, schedules, retries, backoff, dead letters, finance reports, payroll, notifications or deployment are included.
