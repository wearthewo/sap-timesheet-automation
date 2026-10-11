# Phase 8.5 — GitHub Actions and repository quality gates

## Baseline and scope

Inspected AGENTS, README, Maven/Node manifests, wrapper, all Dockerfiles/ignore files, Compose, existing browser suites, local setup scripts and Phase 8 report. Phase 8 was committed as `406ddad` and the working tree was clean. Created `feature/phase8-5-github-actions-ci`. No existing tracked GitHub workflows were present. All fourteen migrations and application business logic remain unchanged. The Phase 8 report remains unchanged.

## Workflows

- `.github/workflows/ci.yml`: PRs targeting main, pushes to main, manual runs. Independent backend, frontend, mock-SAP and workflow-quality jobs. Obsolete runs for the same PR/ref are cancelled. Timeouts 10–25 minutes.
- `.github/workflows/e2e.yml`: main pushes, manual dispatch and Mondays at 03:23 UTC. Complete six-service Compose stack and all existing browser workflows, including SAP retry/reconciliation, notification/Mailpit and downloads. 45-minute bound. Runs on the same ref are serialized rather than cancelled during fixture mutations.
- `.github/dependabot.yml`: weekly Maven, frontend npm/pnpm, Docker and GitHub Actions update PRs. Updates require review and normal CI; no automatic merges.

Both workflows use Ubuntu 24.04 GitHub-hosted runners, only `contents: read`, checkout without persisted credentials, and official actions pinned to immutable SHAs (resolved from upstream release tags). Java uses Eclipse Temurin 21 and Maven Wrapper caching. Node comes from `.nvmrc` (24); pnpm is read from `frontend/package.json` (`pnpm@12.8.1`). Frontend store caching keys include the committed lockfile. `actionlint` is version-pinned to 1.7.7.

Full-stack execution is separated from PR checks because building six services and exercising timed retry/reconciliation paths costs substantially more runner time. Every PR still executes the entire backend PostgreSQL/Testcontainers suite, frontend production build/smoke tests, mock contract tests and configuration linting. E2E can be manually run on a feature branch before merging. No paid service is required; GitHub account runner quotas still apply.

## Exact commands

Backend, working directory `backend`:

```sh
chmod +x mvnw
./mvnw -B -ntp test -DargLine="-Xmx1536m -XX:ActiveProcessorCount=2"
# Copy Surefire reports before clean removes target.
./mvnw -B -ntp clean package -DskipTests
```

Maven runs with `MAVEN_OPTS=-Xmx768m`; Java CPU count is bounded. Docker is available on the GitHub-hosted runner, so Testcontainers uses the normal local socket and Ryuk remains enabled. No WSL paths, Docker Desktop host overrides or H2 are used in GitHub. Integration tests run Flyway on PostgreSQL and Hibernate validates the result.

Frontend, working directory `frontend`:

```sh
npm install --global "$(node -p 'require("./package.json").packageManager')"
pnpm config set store-dir "$HOME/.local/share/pnpm/store" # ephemeral GitHub runner cache
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm build
node ../infra/ci/frontend-smoke.mjs  # starts production server, polls health, runs pnpm test:smoke
pnpm audit --prod --audit-level high
pnpm audit --audit-level high # full report, including dev tooling
pnpm exec playwright install --with-deps chromium
```

The standalone frontend smoke runner generates a temporary random session secret and uses localhost. No live identity server or sensitive build-time values are needed for anonymous smoke tests/builds. Normal OIDC is exercised in E2E.

Root working directory:

```sh
node --test mock-sap/server.test.mjs
docker build --tag ci-mock-sap mock-sap
go install github.com/rhysd/actionlint/cmd/actionlint@v1.7.7
"$(go env GOPATH)/bin/actionlint"
node --test infra/ci/ci.test.mjs
node infra/ci/stack.mjs prepare
node infra/ci/stack.mjs config
node infra/ci/stack.mjs start
node infra/ci/stack.mjs seed
node infra/ci/stack.mjs test
node infra/ci/stack.mjs diagnostics
node infra/ci/stack.mjs stop
```

Mock SAP uses only Node built-ins: there is no package manifest, external dependency or lockfile to install. Its Dockerfile is built in fast CI; full-stack health checks validate startup. `stack.mjs test` runs `pnpm test:smoke`, `pnpm test:browser` and the existing exact-content Mailpit comparator.

## Isolated E2E environment

`prepare` generates random secrets under ignored `.verification/ci` with restrictive file modes. Each run has a random `workforce-ci-<12 hex>` Compose project name, fresh scoped volumes and four synthetic Keycloak users. Application roles and direct-report relationships are seeded in PostgreSQL; Keycloak login is not bypassed. The committed development reporting fixture supplies the seven reporting cases. No production data, services or credentials are used.

Compose receives an explicit generated env file, not the developer's `.env`. `infra/ci/compose.yaml` applies memory limits only to CI. Services remain PostgreSQL, Keycloak, backend, frontend, mock SAP and Mailpit. The backend has a 768 MiB heap; Keycloak 512 MiB; frontend 512 MiB. Health polling uses `docker compose up --build -d --wait --wait-timeout 600`, followed by bounded HTTP checks for health, OIDC, OpenAPI and Swagger. Fixture setup additionally polls initial token issuance with bounded requests: discovery can become ready before the first cold token request completes. No arbitrary startup sleep is used.

Scheduler cadence is accelerated for notification coverage. Fixture setup waits for a real missing-timesheet reminder before seeding historical approved timesheets. The browser checks its recorded period through `TEST_REMINDER_MONTH`, avoiding the old fixed October 2026 assumption. Backend deterministic Clock tests remain responsible for independently checking calendar/timezone rules. A run crossing Athens midnight can legitimately receive a second daily reminder and must be rerun; ordinary repeated evaluation remains asserted not to duplicate.

Local verification can set `CI_FRONTEND_PORT`, `CI_BACKEND_PORT`, `CI_KEYCLOAK_PORT`, `CI_POSTGRES_PORT`, `CI_MAILPIT_WEB_PORT`, `CI_MAILPIT_SMTP_PORT` before prepare to avoid development ports. Optional `CI_BROWSER_CONTAINER` uses a local Node 24 Playwright image instead of host pnpm; GitHub does not use this path. Stop removes only the validated unique CI project/volumes. It never executes `down -v` against the development project. Existing state is not silently overwritten; after stopping, remove only the ignored CI state file to prepare a new run.

## Diagnostics and security

Backend Surefire reports are saved before clean packaging and uploaded for seven days. E2E always attempts diagnostics and teardown, including on failure. Redacted service logs, service state and safe job/notification execution columns are retained for three days, alongside JUnit browser results and screenshots/downloads containing synthetic test data. No env dump, fixture credentials, database dump, SMTP body dump or raw HTTP capture is uploaded.

Playwright traces remain disabled intentionally: they can contain Keycloak passwords, session cookies and bearer tokens. This preserves the existing security convention rather than publishing unsafe traces. Generated secrets are masked in GitHub logs and removed from textual test artifacts before upload. Failure screenshots remain the existing explicit screenshots; automatic login screenshots/video are disabled. Hard runner termination can prevent cleanup; GitHub-hosted disposable runners provide the final isolation boundary.

Reviewed `.gitignore`: `.env*` (except example), `.verification`, toolchains, dependencies, builds and Playwright artifacts are excluded. Added `.pnpm-store/` to keep generated package caches out of commits. Added backend Docker-context exclusions for env files, verification data and logs; frontend context now also excludes verification data/logs. Mock context already allowlists only its server and Dockerfile.

`pnpm audit --prod --audit-level high` blocks CI on high/critical production findings or audit failures. A second full `pnpm audit --audit-level high` includes development tooling, publishes its complete output as a seven-day artifact, and emits a visible workflow warning on findings or registry errors. The full report is advisory, not a passing vulnerability assertion. Review both reports; all application tests remain blocking. Enable GitHub dependency graph/Dependabot alerts/security updates in repository settings where available; configuration alone does not enable account-level features or establish vulnerability-free Maven/container dependencies.

The current development-tooling finding is [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm): deeply nested brace patterns can exhaust the stack in `braces <=3.0.3`. The observed path is `eslint-config-next > @next/eslint-plugin-next > fast-glob > micromatch > braces`, in development lint tooling. The advisory lists no patched release. Checking `npm view @next/eslint-plugin-next@latest version dependencies --json` returned 16.4.0 with the same `fast-glob: 3.3.1` dependency. No unverified upgrade or advisory suppression was applied. The initial all-dependency blocking audit failed; the final policy explicitly separates production gating from development-tooling reporting. This finding remains visible and unresolved. Its nested-pattern denial-of-service concerns repository-controlled lint input in a bounded, disposable CI process; it is not a production dependency or evidence of a runtime SAP/timesheet defect. This is a documented risk decision, not a claim that the package is fixed. Reassess when upstream publishes a fix; do not blindly update application dependencies to address a lint-only issue.

The production smoke runner launches `.next/standalone/server.js` and copies static assets into its standalone directory, matching the existing Docker runtime. This avoids Next.js's warning about using `next start` with standalone output. Only the disposable build output is changed.

## Observed verification

No GitHub-hosted run has been executed or claimed successful. Local verification began on 2026-10-10 and continued on 2026-10-11 (Europe/Athens).

| Check | Local result | Evidence |
|---|---|---|
| Full Java 21/Testcontainers suite | PASS: 300 tests, zero failures/errors/skips | `.verification/backend85/test.log`, Surefire XML |
| Clean production package | PASS | `.verification/backend85/package.log` |
| Mock SAP | PASS: 7 tests, repeated on scoped Node 24 | `.verification/phase85-mock-final.log` |
| CI helper tests | PASS: 5 tests | Node 24 native and container runs |
| actionlint 1.7.7 with shellcheck | PASS after quoting correction | Pinned actionlint Docker runtime |
| Compose validation/build/start | PASS: fresh rerun, all six healthy; health/OIDC/OpenAPI/Swagger reachable | `.verification/phase85-rerun-start.log` |
| Synthetic fixture setup | PASS: four OIDC roles, relationships, assignments, SAP and report fixtures | `.verification/phase85-rerun-seed.log` |
| Frontend frozen install/lint/typecheck/build | PASS | `.verification/phase85-frontend-final.log` |
| Frontend production smoke | PASS: 18 tests against the corrected standalone runner | `.verification/phase85-standalone-smoke.log` |
| Production dependency audit | PASS: no known vulnerabilities reported | `.verification/phase85-production-audit.log` |
| Full development-inclusive audit | FINDING: high-severity `braces` advisory GHSA-vfj7-8cjw-p6xm, via Next ESLint tooling; audit reports no patched version | `.verification/phase85-frontend-final.log` |
| Full-stack smoke | PASS: 18 tests | `.verification/phase85-rerun-browser.log` |
| Full browser run | PASS: 10 tests, no retries/skips, 8.1 minutes | `.verification/phase85-rerun-browser.log`, `frontend/test-results/browser.xml` |
| Exact-content Mailpit comparator | PASS: 26 delivered intents, all seven notification types | `.verification/phase85-rerun-browser.log` |
| Diagnostics and teardown | PASS: redacted diagnostics collected; only isolated CI containers/volumes removed; original six development services remain healthy | `.verification/ci/diagnostics/`, `.verification/phase85-rerun-stop.log` |

Phase 8.5 implementation and local functional verification are complete, with the explicitly documented development-dependency risk above. Production audit passes. The initial browser run had nine passes and one login failure while another resource-intensive frontend build was running; resource contention is a hypothesis, not a proven root cause. The clean isolated rerun passed all ten unchanged workflows with zero retries. No application timeout was increased to obtain this result. GitHub-hosted workflows have not run and are not claimed green. Changes remain uncommitted on `feature/phase8-5-github-actions-ci`.

Mailpit exact matches: employee reminders 2, manager reminders 4, rejections 2, approvals 2, SAP FAILED 6, SAP UNKNOWN 8, finance monthly reminders 2. The comparator checks persisted intent against actual recipient, subject, UTF-8 plain text, links/comments and one matching physical message per intent; HTML is absent by the existing contract. These results do not imply exactly-once SMTP after ambiguous acceptance.

Browser coverage includes the complete reject/reopen/resubmit/approve cycle, notification history privacy, reporting projections and CSV/XLSX downloads, SAP export and SAP XLSX, role restrictions, worker retry/reconciliation, mobile navigation and logout. The suite ran against fresh PostgreSQL/Keycloak/Mailpit/mock data. No new process-kill recovery experiment was introduced in Phase 8.5; existing backend and browser regression coverage was preserved.

Corrections found during verification: actionlint caught a shell-quoting warning; a new helper test's 100 ms success deadline was too short under host load (the final test preserves exact retry-count assertions with a reasonable bound); cold Keycloak startup exceeded the initial readiness window; first token issuance needed its own readiness check; Keycloak's name validation rejected special-character fixture names, so simple OIDC names are used while all original special-character assertions continue against authoritative application display names. The Windows-bound backend verification was restarted on a container-local filesystem and then passed the full suite. Slow registry downloads were observed during the frozen frontend install. No application tests were removed or skipped.

Runtime commands required outside-sandbox execution for Docker, network dependency installation and the scoped Node 24 download. Java 21 ran in Docker; Node 24 ran in Docker and an ignored repository-local runtime. No unrelated global developer toolchain was changed. Git branch creation also needed outside-sandbox permission because `.git` is read-only in the sandbox.

## GitHub requirements and reruns

Push this branch and open a PR targeting main. Enable Actions and permit the pinned official actions. No repository/environment secrets or OIDC cloud permissions are needed. Manual dispatch and schedules generally require the workflow to exist on the default branch first; once available, choose the desired branch in Actions → Full-stack E2E → Run workflow. A failed run can be rerun from Actions without weakening tests. Scheduled runs use the default branch.

Configure branch protection to require the stable CI jobs `backend`, `frontend`, `mock-sap`, `quality` after observing their actual check names. Do not require the push/manual-only E2E job on every PR unless its trigger policy is also changed. Branch protection, Dependabot settings, cache behavior, artifact uploads, runner capacity and actual hosted execution still need GitHub confirmation. Badge URLs use the inspected origin `wearthewo/sap-timesheet-automation`; they are not a claim of passing runs.

For failures: inspect Surefire XML for backend assertions/Flyway errors; frontend logs for lint/type/build/audit failures; E2E service logs/health for startup/OIDC issues; safe job columns for retry/reconciliation stalls; JUnit/screenshots for browser assertions. Do not disable Ryuk, skip tests, increase retries to hide failures or upload secrets to diagnose a failure. No merge, deployment or business/schema redesign is part of this phase.
