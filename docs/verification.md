# Verification record

Builds and automated tests verified on 2026-10-03; final browser verification continued on 2026-10-04. No deployment was performed.

## Required toolchains

The host exposed Node 22.11.0, no Java on PATH, and no pnpm. A normal Windows Maven Wrapper attempt failed before Maven could start. The sandbox could not access the Docker daemon or package registries reliably.

Verification therefore used approved outside-sandbox Docker execution:
- maven:3.9-eclipse-temurin-21, Java 21.0.12.1; repository Maven Wrapper 3.9.11.
- node:24-bookworm-slim with pnpm 12.8.1 installed inside disposable containers.
- Docker Desktop's Linux engine and real PostgreSQL 17 Testcontainers.

No host Java/Node installations or unrelated global configuration were replaced. Docker dependency/build caches are project-named volumes. Frontend node_modules and .next were moved into named volumes after the Windows bind mount proved too slow.

## Commands and results

- java -version / node --version / pnpm --version / docker version: toolchain discovery; Java/pnpm absent, Node 22, sandbox Docker access denied.
- Official Maven wrapper generation: mvn -B -ntp wrapper:wrapper -Dmaven=3.9.11 -Dtype=only-script. An initial PowerShell quoting error was corrected before the successful run.
- .\\mvnw.cmd test on host: could not bootstrap in the sandbox.
- ./mvnw -B -ntp test in Java 21 container: **21 tests, 0 failures, 0 errors, 0 skipped**.
- pnpm install, then pnpm install --frozen-lockfile: passed after configuring the explicit unrs-resolver build allowlist for pnpm 12.
- pnpm lint: first run had one PostCSS style warning; the config was corrected and ESLint rerun with zero warnings/errors.
- pnpm typecheck: passed (next typegen and tsc --noEmit).
- pnpm build: passed; also passed independently inside the frontend Docker build.
- docker compose config --quiet: passed. The sandbox run warned about access to the Docker user config; the outside-sandbox run passed.
- docker compose up -d postgres keycloak: succeeded after selecting local host port 55432 because Windows blocked 5432.
- docker compose build backend and docker compose up -d backend: passed.
- docker compose up -d --build frontend: built the complete dependency stack and started all four services.
- docker compose ps: PostgreSQL, Keycloak, backend and frontend all healthy.
- GET /actuator/health: UP.
- GET /v3/api-docs: successful OpenAPI document with the Phase 1 paths.
- pnpm lint (final): passed with zero warnings/errors.
- pnpm test:smoke: **7 tests passed, 0 failures, 0 skipped** against the live Compose frontend.
- Browser: anonymous /app access redirected to /login; the initial live callback exposed a discovery/backchannel address mismatch. Explicit OIDC endpoints fixed that configuration. The live test also identified the required Keycloak basic client scope; the realm import now includes it, and the backend explicitly rejects a missing subject. The updated 21-test suite passed. Final browser results are recorded below.

The first frontend dependency install on a Windows bind mount was stopped and restarted using Docker volumes; it did not complete verification. No failed or interrupted run is counted as a pass.

## Reproduce scoped verification on Windows

From the repository root, with Docker Desktop running:

```powershell
$repo = (Get-Location).Path
docker run --rm --mount "type=bind,source=$repo\backend,target=/work" --mount "type=volume,source=workforce-maven-cache,target=/root/.m2" --mount "type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock" -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e JAVA_TOOL_OPTIONS=-Dapi.version=1.44 -w /work maven:3.9-eclipse-temurin-21 sh -c 'chmod +x mvnw && ./mvnw -B -ntp test'
docker run --rm --mount "type=bind,source=$repo\frontend,target=/work" --mount "type=volume,source=workforce-node-modules,target=/work/node_modules" --mount "type=volume,source=workforce-next-build,target=/work/.next" -e NEXT_TELEMETRY_DISABLED=1 -w /work node:24-bookworm-slim sh -c 'npm install -g pnpm@12.8.1 && pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm build'
```

The Docker socket is required only by the test runner, never by application containers. The API-version setting supports this Docker 29 host with Testcontainers' Docker client. On Linux, use the equivalent bind-mount paths and an appropriate host address for Testcontainers.

After Compose startup, run pnpm test:smoke from a Node 24 frontend environment. SMOKE_BASE_URL defaults to http://localhost:3000; a container on workforce_default uses http://frontend:3000.

## Coverage

Tests exercise first login, existing users, concurrent provisioning, provisioning on other API reads, anonymous/invalid-token rejection, EMPLOYEE/MANAGER/FINANCE write denial, ADMIN creation/update, duplicates, missing resources, malformed requests and bounded pagination, assignments and invalid dates, foreign keys, unique employee numbers, active/date/owner filtering, no-employee empty results, health and OpenAPI.

JWT security tests use Spring mock JWTs and a mock decoder, plus direct tests of the production issuer/audience/expiry/subject validator, not a live Keycloak requirement. Live OIDC verification complements those tests. Tests use real Flyway migrations and PostgreSQL; no H2 or skipped Docker tests.

## Dependencies and compatibility references

The latest stable patch of the Spring Boot 3.5 line was selected with [springdoc's Boot 3.5 / 2.8 compatibility guidance](https://springdoc.org/v2/). Next.js uses the [Next.js 16 App Router](https://nextjs.org/blog/next-16). pnpm configuration follows the [pnpm workspace settings reference](https://pnpm.io/settings). Version pins and the committed lockfile are the reproducible dependency sources.

## Final browser and runtime results

On 2026-10-04, a real Keycloak authorization-code/PKCE login succeeded through Next.js. Spring Boot validated the access token and provisioned one EMPLOYEE application user. The authenticated dashboard showed the user's name and role.

A local-only QA-001 employee and PHASE1-DEMO project/assignment were inserted for UI verification. The project page showed one current assignment and the expected project. At a 375 × 812 viewport:
- No horizontal overflow.
- Navigation and logout visible.
- Logout button not underlined.
- Browser-visible /api/auth/session contained no token fields.

Logout cleared the local session, displayed Keycloak's logout confirmation, and returned to /login after confirmation. The verification browser was closed.

Final docker compose config --quiet passed; all four services were healthy. Backend /actuator/health returned UP and /v3/api-docs returned five paths representing the seven documented API operations.

The updated backend suite passed 21 tests with no failures, errors or skips. The final rerun compiled a copy of the source in the container filesystem to reduce Windows bind-mount overhead, then copied Surefire reports back to backend/target/surefire-reports. Frontend lint/typecheck and Docker production build passed after the OIDC correction. All seven frontend HTTP smoke tests passed against the rebuilt image.

Local services remain running at http://localhost:3000. Generated local secrets are only in ignored .env; the generated verification account credentials are only in ignored .verification/test-user.json. No secrets are committed. The sample employee/project data exists only in the local Compose PostgreSQL volume.

## Outside-sandbox execution

Outside-sandbox approval was used for Docker runtime downloads, Maven Wrapper generation, Java 21 tests/builds with Testcontainers, Node 24 dependency installation and pnpm verification, Compose startup/builds and live checks, package registry version lookups, and headless browser/Keycloak local verification. Normal sandbox Compose configuration validation also succeeded, with a Docker user-config access warning.

No production resources were created. No Render or Vercel deployment commands were run. Host Java/Node versions and unrelated global settings were not changed.

## Remaining deliberate Phase 1 limits

New Keycloak users/passwords must be created in the Keycloak console on a clean installation. Application employee onboarding, manager assignment and ADMIN promotion remain trusted operator SQL operations documented in README.md. Tokens require reauthentication at expiry; automatic refresh is not implemented. Local Keycloak start-dev must be replaced with a production Keycloak configuration before any future deployment.

Timesheets, approvals, reporting, SAP integration, export processing, messaging, notifications and payroll remain unimplemented.
