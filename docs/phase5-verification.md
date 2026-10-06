# Phase 5 verification

Verified locally on 2026-10-06. Nothing was deployed and Phase 6 was not started.

## Results

| Check | Result |
| --- | --- |
| Maven Wrapper tests, Java 21 and Testcontainers PostgreSQL | 133 passed; zero failures, errors or skips |
| Phase 1 / 2 / 3 / 4 / 5 integration suites | 21 / 29 / 22 / 28 / 33 passed |
| Maven clean package, tests skipped after the test run | Passed |
| pnpm frozen install / lint / typecheck / production build | All passed |
| Frontend smoke tests | 13 passed |
| Playwright browser tests | 6 passed |
| Mock SAP Node HTTP tests | 7 passed |
| Compose configuration / production image build and startup | Passed |
| PostgreSQL / Keycloak / backend / frontend / mock-sap | All healthy |
| Backend health / OpenAPI / Swagger | UP / SAP paths present / HTTP 200 |
| git diff --check | Passed |

V10 and V11 applied successfully to the existing local database. Existing export records received corresponding jobs. V1-V9 were not modified. Existing Phase 4 tests were adapted to the explicitly changed asynchronous 202 response and idempotent enqueue behavior; earlier workflow tests remain in place.

## Backend coverage

Tests exercise atomic/idempotent enqueue, authorization, bounded SKIP LOCKED claims, competing claimers, future scheduling, fresh leases, expired-lease recovery, stale-result fencing, immutable attempt history, success persistence and safe errors. They cover transient 500/503 and connection failures, capped backoff, exhausted budgets, permanent validation/authentication failures, manual retry/reconciliation authorization and invalid states.

UNKNOWN tests distinguish timeout before creation from timeout after creation, require lookup before resending, recover an existing reference, and retain UNKNOWN when lookup fails. Transaction tests check that claims/reservations commit before HTTP and that export/job/history changes commit or roll back together. Crash recovery is tested by simulating expired persisted PROCESSING claims in PostgreSQL; a live process was not killed during an outbound request.

## Browser and mock verification

Playwright exercised the real Keycloak, Next.js BFF, Spring backend, PostgreSQL and mock SAP stack using fresh local fixtures. It verified existing employee entry and manager approval flows, then:

1. FINANCE queues an approved timesheet, observes asynchronous progress and a successful external reference; repeating the command does not create another logical export.
2. Two transient 503 failures cause reconciliation and scheduled retries before the third export succeeds.
3. A timeout after SAP persistence becomes UNKNOWN; reconciliation finds the existing record and succeeds with only one outbound export attempt.
4. Four transient failures exhaust the automatic budget; ADMIN manually retries the same export/job and the fifth export succeeds, preserving the nine-operation history.
5. Validation failure remains terminal with a safe message and no retry action.
6. EMPLOYEE/MANAGER views remain read-only; mobile navigation and logout continue to work.

Desktop reconciliation and mobile retry screenshots were visually reviewed. Navigation/logout remain accessible, controls fit the viewport, and history, references and safe failure messages are readable. Screenshots remain ignored under frontend/test-results.

Mock tests use actual HTTP requests for authentication, validation, duplicate detection, persisted records, identity lookup, transient responses and timeout-before/after-creation behavior. Credentials and fixture configuration remain in ignored local files.

## Commands and execution environment

All Docker, runtime, build, Testcontainers, browser and local-service network verification used outside-sandbox execution. No global Java, Node or pnpm installation was replaced. Source edits and static repository checks used the normal workspace.

Backend commands ran through the repository Maven Wrapper in maven:3.9-eclipse-temurin-21, with a container-local working copy, the existing Maven cache, Docker socket access for Testcontainers, TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal and JAVA_TOOL_OPTIONS=-Dapi.version=1.44:

```sh
./mvnw -B -ntp test
./mvnw -B -ntp clean package -DskipTests
```

Frontend commands ran in the existing Node 24 / pnpm 12.8.1 browser verification image, using the Compose network for integration tests:

```sh
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm build
pnpm test:smoke
pnpm test:browser
```

Additional commands:

```sh
# In a Node 24 container with mock-sap mounted as its working directory:
node --test server.test.mjs
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
git diff --check
```

Local HTTP checks confirmed /actuator/health, /v3/api-docs and /swagger-ui/index.html. Browser verification used shorter local-only polling/backoff/timeouts documented in sap-export-worker.md; committed defaults remain suitable for ordinary development.

## Issues found and fixed

- A recovered job scheduled during a claim transaction could be excluded by PostgreSQL now(), which is fixed at transaction start. Eligibility now uses clock_timestamp(); the lease-recovery regression passes.
- A manual-action query joining locked jobs to exports could observe an old export snapshot after waiting behind worker completion. It now locks the job first and reads export state in a subsequent statement. A deterministic lock-wait regression passes.
- Claimed operations start as a bounded concurrent batch so later jobs do not consume their lease waiting for earlier HTTP calls. Claim tokens reject late results from replaced leases.

The integration remains at-least-once with remote idempotency, not exactly-once network delivery. Production SAP must provide equivalent atomic identity enforcement and authoritative lookup semantics before a real adapter is enabled. There are no production SAP credentials, external brokers, new deployment services, reports, payroll or notifications.
