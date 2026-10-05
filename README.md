# Employee Timesheet & SAP Automation Platform

Phase 2 adds employee monthly timesheets to the Phase 1 identity, employee, project and assignment foundation. Employees can record decimal hours on assigned projects, save drafts, see daily/monthly totals and submit immutable monthly timesheets.

**No approvals, reporting, SAP integration, workers, messaging, notifications or payroll are implemented. Nothing is deployed.**

## Architecture and stack

Browser → Render Next.js service / BFF → Render Spring Boot service → Render PostgreSQL

Future only: Spring Boot → SapTimesheetGateway → SAP S/4HANA

See [architecture](docs/architecture.md) for the diagram, authentication boundaries and future modules.

- Java 21, Spring Boot 3.5.16 (stable 3.5 line), Maven Wrapper 3.9.11.
- Spring Web, Security/JWT, Data JPA, Bean Validation, Flyway, PostgreSQL, springdoc 2.8.17.
- Next.js 16.3.8 App Router, React, strict TypeScript, Tailwind, Node 24, pnpm 12.8.1.
- Keycloak 26.8.0 with NextAuth 4.24.15 OIDC; JUnit 5 and Testcontainers PostgreSQL.
- Node server/standalone output, not static export; no Vercel services.

```text
backend/       Spring Boot feature modules, Flyway, integration tests, Maven Wrapper
frontend/      Next.js UI, OIDC session handling, server-side backend calls
infra/         Local Keycloak realm import and PostgreSQL initialization
docs/          Architecture and verification notes
compose.yaml   Four-service local stack
.env.example   Configuration template (no secrets)
AGENTS.md      Coding and scope rules
.nvmrc         Node 24
```

## Local startup

Install Docker Desktop/Engine with Compose. Java and Node are not required on the host for Compose.

1. Copy .env.example to .env.
2. Fill DATABASE_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_CLIENT_SECRET and NEXTAUTH_SECRET with independent random values (e.g. openssl rand -hex 32).
3. Run:
   ```sh
   docker compose config --quiet
   docker compose up --build
   ```
4. Open http://localhost:3000; Keycloak administration is http://localhost:8180/admin.

PostgreSQL data persists in the postgres_data volume. The local keycloak database is created on first volume initialization. Realm imports also run only when the realm does not exist: changing secrets or URLs in .env after initial import requires updating the Keycloak client in its console. Do not delete persistent volumes to apply normal configuration changes.

Local published ports bind to 127.0.0.1. Port variables control host mappings; when changing frontend/Keycloak ports also update the public URLs, issuer and client settings consistently. Compose-internal addresses are set separately from browser-visible URLs.

## Keycloak setup and first user

The import creates realm workforce and confidential client workforce-web:
- Standard authorization code flow with PKCE S256; password grant and service accounts disabled.
- Redirect: http://localhost:3000/api/auth/callback/keycloak.
- Web origin: http://localhost:3000.
- Post-logout redirect: http://localhost:3000/login.
- Access-token audience: workforce-api.
- Default scopes include basic (required for the access-token sub), profile, email and web-origins.
- Issuer: http://localhost:8180/realms/workforce.
- Client secret comes from KEYCLOAK_CLIENT_SECRET through realm environment substitution.

Log into the admin console with the bootstrap credentials from .env, select workforce, create a test user with username/email/first/last name, and set a password in Credentials. Set Temporary off for a simple local test, or complete the required password update at first login. No test passwords or users are committed.

Sign in through the frontend once; /api/v1/auth/me provisions an application User with EMPLOYEE role. No employee record is invented from JWT claims.

### Operator onboarding and admin bootstrap

Use a trusted database session, e.g. docker compose exec postgres psql -U workforce -d workforce (adjust username). Look up the exact Keycloak subject from the Keycloak user record. Do not select a user by unverified frontend input or promote based on email alone.

```sql
-- Replace all example values. First confirm the expected subject and identity.
SELECT id, external_subject, email, role FROM app_users;
UPDATE app_users SET role = 'ADMIN', updated_at = now()
WHERE external_subject = '<verified-keycloak-subject>';

INSERT INTO employees (id, user_id, employee_number, active)
SELECT gen_random_uuid(), id, 'E001', true
FROM app_users WHERE external_subject = '<verified-keycloak-subject>';

-- Optional manager, using verified employee UUIDs:
UPDATE employees SET manager_id = '<manager-employee-uuid>', updated_at = now()
WHERE id = '<employee-uuid>';
```

Roles are exclusively database-owned. No endpoint accepts role changes. MANAGER and FINANCE currently have authenticated read access, like EMPLOYEE. Only ADMIN can write projects and assignments. Employee number duplicates are rejected by the database and mapped by the shared error handler when surfaced through application code.

Use Swagger UI with a valid Keycloak access token and an ADMIN application user to create projects and assignments. Tokens must include the workforce-api audience; never commit or log them.

## Employee timesheets

Open Timesheets in the authenticated navigation, choose a month, create a draft, and save individual entries. Each save persists progress. Submit after checking totals; submitted months are read-only. The lifecycle is DRAFT → SUBMITTED, with no reverse transition. Approval comes in Phase 3.

See [timesheet usage, API and concurrency rules](docs/timesheets.md), including version headers, ownership, assignment-date validation, daily limits, and browser-test setup.

## Environment variables

| Variable | Purpose |
|---|---|
| DATABASE_URL | Backend JDBC PostgreSQL URL; use jdbc:postgresql://... (convert Render connection URI) |
| DATABASE_USERNAME / DATABASE_PASSWORD | Application database credentials |
| KEYCLOAK_ISSUER_URI | Public, exact token issuer, including /realms/workforce |
| KEYCLOAK_JWK_SET_URI | Backend-reachable signing-key endpoint |
| KEYCLOAK_AUDIENCE | Required access-token audience, workforce-api |
| KEYCLOAK_INTERNAL_URL | Frontend server-reachable realm URL for token/userinfo/JWKS |
| KEYCLOAK_CLIENT_ID / KEYCLOAK_CLIENT_SECRET | Confidential frontend OIDC client |
| NEXTAUTH_URL / NEXTAUTH_SECRET | Canonical frontend URL and session encryption secret |
| BACKEND_API_URL | Next.js server-to-backend base URL |
| FRONTEND_URL | Compose public frontend URL and realm redirect origin |
| KEYCLOAK_PUBLIC_URL | Compose public Keycloak hostname URL |
| KEYCLOAK_ADMIN_USERNAME / KEYCLOAK_ADMIN_PASSWORD | Local Keycloak bootstrap account |
| CORS_ALLOWED_ORIGINS | Comma-separated exact origins, local or future Render frontend |
| MAX_DAILY_HOURS | Backend maximum total hours per work date across projects; default 24 |
| PORT | Runtime listening port, respected by both applications |

Root .env is read by Compose. For host development, export backend variables into the shell; copy relevant frontend variables into frontend/.env.local. Never use NEXT_PUBLIC_ for secrets. The browser does not call Spring Boot directly. Next.js attaches a bearer token server-side and disables fetch caching for user data.

## API

| Method | Path | Access |
|---|---|---|
| GET | /api/v1/auth/me | Authenticated; provisions first user |
| GET | /api/v1/projects | Authenticated, paginated catalog |
| GET | /api/v1/projects/{id} | Authenticated |
| GET | /api/v1/employees/me/projects | Authenticated, current assignments |
| POST | /api/v1/projects | ADMIN |
| PUT | /api/v1/projects/{id} | ADMIN |
| POST | /api/v1/project-assignments | ADMIN |

Lists accept page (zero-based) and size (1–100, default 20). POSTs return 201; project creation includes Location. Project write fields: code, name, description (optional), active. Assignment write fields: employeeId, projectId, validFrom, validTo (optional), active. PUT is full replacement of editable project fields.

Errors use timestamp/status/code/message/path. Expected responses include 400 validation/date errors, 401 unauthenticated, 403 forbidden, 404 not found and 409 duplicates/conflicts.

- Health: http://localhost:8080/actuator/health
- OpenAPI: http://localhost:8080/v3/api-docs
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Frontend liveness: http://localhost:3000/api/health

Future TypeScript client generation will consume /v3/api-docs using a pinned OpenAPI generator in CI. The frontend has only minimal presentation types, not a manually maintained duplicate schema.

## Development and verification

Latest results and executed commands: [Phase 2 verification](docs/phase2-verification.md).

Use a Java 21 version manager and Node 24 (nvm use with the root .nvmrc), or the scoped container procedure in [verification](docs/verification.md). No global replacement is required.

```sh
cd backend
./mvnw test
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

On Windows use .\\mvnw.cmd test. Maven enforces Java 21 and compiler release 21. Tests require Docker and start their own PostgreSQL container, run real Flyway migrations and validate Hibernate mappings. They do not use H2 or require live Keycloak. Security tests use mock JWTs.

```sh
cd frontend
corepack pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm build
pnpm dev
# With the application running:
pnpm test:smoke
```

Use pnpm 12.8.1; corepack must be installed/enabled in your chosen Node environment if used. Alternatively install pnpm inside a disposable Node 24 container. The lockfile is committed. Frontend builds do not require a live backend or secrets.

## Future Render deployment (not performed)

Backend root backend: ensure mvnw is executable (chmod +x mvnw after a Windows-created checkout), build ./mvnw clean package -DskipTests, start java -jar target/workforce-0.1.0.jar. Render can use the supplied Java 21 Dockerfile. PORT controls Spring Boot.

Frontend root frontend: install pnpm install --frozen-lockfile, build pnpm build, start pnpm start. Set Node 24 and pnpm 12.8.1. next start reads PORT; Docker uses standalone server.js with PORT and HOSTNAME=0.0.0.0.

Use Render's private backend/database addresses for server traffic, HTTPS public frontend/Keycloak URLs, and secret environment variables. Supply a production Keycloak deployment separately; local start-dev is not suitable. Set exact issuer, JWKS reachability, audience, redirect/logout URIs and canonical NEXTAUTH_URL. No production URLs are hardcoded.

See [architecture](docs/architecture.md) for future module boundaries. Approval will be introduced in Phase 3. Phase 3 has not started.
