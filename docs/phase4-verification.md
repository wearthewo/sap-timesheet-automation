# Phase 4 verification

Verified locally on 2026-10-06. Nothing was deployed. Phase 5 was not started.

## Final results

| Check | Result |
| --- | --- |
| Maven Wrapper test, Java 21 / Testcontainers PostgreSQL 17 | 100 passed, 0 failures, 0 errors, 0 skipped |
| Existing Phase 1 / 2 / 3 suites | 21 / 29 / 22 passed |
| New PhaseFourIntegrationTest | 28 passed |
| Maven clean package -DskipTests | Passed |
| pnpm install --frozen-lockfile | Passed; existing lockfile retained |
| pnpm lint | Passed |
| pnpm typecheck | Passed |
| pnpm build | Passed, including final Compose production build |
| pnpm test:smoke | 13 passed, 0 failures |
| pnpm test:browser | 4 passed, 0 failures, no retries |
| Mock SAP native Node HTTP tests | 3 passed |
| docker compose config --quiet | Passed |
| docker compose up -d --build --wait | Passed |
| PostgreSQL / Keycloak / backend / frontend / mock-sap | All healthy |
| /actuator/health | UP |
| /v3/api-docs | Available, all SAP paths present |
| /swagger-ui/index.html | HTTP 200 |

Backend tests use real PostgreSQL and a deterministic HTTP server, with mock JWT authentication rather than live Keycloak. They cover eligibility, all four roles, anonymous requests, stale versions, field/decimal mapping, persistence, duplicate prevention, 400/401/403/409/500/503/redirect responses, invalid JSON/references, timeout ambiguity, safe redaction, owner/direct-manager read access, committed reservation visibility and no idle database transaction during HTTP. Concurrent requests produce one reservation and one outbound operation.

The mock tests exercise real HTTP authentication/validation, success lookup, duplicate detection across restarts and an accepted request whose response times out. The adapter integration tests do not require a mock Compose service.

## Browser verification

Playwright used the real Next.js BFF, Spring backend, PostgreSQL, Keycloak and mock SAP. Fresh local fixtures preserved all prior audit history. The suite verified:

1. Existing employee entry creation, editing, deletion, decimal totals, stale-tab conflict, submission and read-only state.
2. Existing manager rejection with required comment, explicit employee reopening, resubmission, approval and preserved decision history.
3. FINANCE opens an approved fixture, exports, sees SUCCEEDED and its SAP reference, then reloads and cannot issue a second export.
4. ADMIN exports a second approved fixture containing MOCK-INVALID and sees FAILED with the safe message “SAP rejected the timesheet data.”
5. EMPLOYEE and MANAGER see read-only export state and no export action; the backend tests separately verify forbidden POST requests.
6. Mobile navigation, no horizontal overflow, and logout for the relevant roles.

Reviewed the generated desktop success and mobile failure screenshots. They show visible navigation/logout, wrapped controls, safe errors and external reference. Screenshots remain ignored under frontend/test-results; no login traces or credentials were captured.

## Commands and scoped runtimes

All runtime/build/browser/Docker verification used approved **outside-sandbox execution**. The host toolchains were not replaced. Java remained 21 and Node remained 24; pnpm remained 12.8.1.

Backend ran in maven:3.9-eclipse-temurin-21 with the existing Maven cache volume, a native container work directory copied from backend, Docker socket access for Testcontainers, TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal and JAVA_TOOL_OPTIONS=-Dapi.version=1.44:

```sh
./mvnw -B -ntp test
./mvnw -B -ntp clean package -DskipTests
```

Surefire reports and the test log were copied into ignored backend/target before the clean package step. Container-native compilation avoids slow Windows bind-mounted compilation.

Frontend ran in the existing workforce-browser-tests Node 24/pnpm/Chromium image with project source and the existing node_modules/.next cache volumes:

```sh
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm build
pnpm test:smoke
pnpm test:browser
```

Smoke tests targeted http://frontend:3000 on the Compose network. Browser tests reached localhost services through the established Chromium host mapping. Credentials were read from ignored local fixture files into process-scoped environment variables and removed afterward, never printed or committed. No new global tool installation was performed.

Additional executed commands:

```sh
node --test server.test.mjs                 # mock-sap directory, node:24-bookworm-slim
docker compose config --quiet
docker compose up -d --build --wait
docker compose up -d --build --wait mock-sap
docker compose up -d --build --wait frontend
docker compose ps
```

Local HTTP health/OpenAPI/Swagger probes and trusted local Keycloak/database test onboarding also required outside-sandbox access. Repository edits, static inspection and git diff --check ran normally in the workspace.

## Issues found and fixed

- A malformed 2xx JSON body was initially wrapped as a transport error. It now maps to SAP_EXPORT_UNKNOWN and persists UNKNOWN; all four malformed-success cases pass.
- Initial Compose editing duplicated a service block inside depends_on. Corrected before successful Compose validation/startup.
- New smoke routes initially ran against the old frontend container. Rebuilt the stack and reran: all 13 pass.
- Existing approval browser navigation did not wait for the month URL. Multiple approved fixtures made its broad status assertion race with the list page. Added an explicit URL wait, preserving the original workflow assertions; the full four-test suite then passed.
- Export timestamps now render explicit UTC to avoid locale-dependent server/browser hydration differences.
- Mock persistence failures return a server failure rather than pretending the request was invalid.

## Scope and limitations

V9 is the only new migration. Existing migrations and TimestampedEntity are unchanged. No production SAP contract has been certified; the mock is intentionally SAP-shaped. One durable export record per timesheet is the source of truth. The deterministic identity survives response loss and process restarts. FAILED/UNKNOWN/PENDING records cannot be retried in this phase; PENDING can remain stranded after a process or finalization failure. Reconciliation and retry orchestration remain future work.

The application approval status remains APPROVED after export. No worker, scheduler, automatic retry, report, payroll, notification or deployment was introduced. See [SAP integration](sap-integration.md) for APIs, contract, database/network boundaries and operational semantics.
