# Phase 12 production-readiness checkpoints

Branch: `feature/phase12-production-readiness`. Checkpoint 12A only; changes remain uncommitted. No deployment, push, merge, legacy database migration or original Keycloak realm change is authorized here.

| Checkpoint | Status |
| --- | --- |
| 12A — repository audit and test reliability | Complete: isolated fixtures, full single-run browser pass and security regression verified |
| 12B — professional UX and dashboard | Not started |
| 12C — security and performance | Not started |
| 12D — production infrastructure | Not started |
| 12E — final verification | Not started |

## 12A scope and decisions

Reviewed repository rules, README, architecture/security documentation, Phase 9–11 reports, the ignored live-QA report, CI jobs, Dockerfiles, Compose, browser tests and CI fixtures. Branch and working tree were clean at entry. Preserve Java 21, Node 24, pnpm, Keycloak, tenant-owned database roles, all migrations and business behavior.

- Onboarding creates its own collision organization using a second real registered user. Duplicate-slug, verification, private session, mobile/logout and password-recovery assertions remain.
- Seven seeded browser specs each receive independent accounts and two organizations. No worker mutates process-wide credentials or falls back to developer users. Two tests within a spec use independent months/exports or read-only data; the mobile timesheet scenario owns a pre-submitted month and no longer depends on the editing test.
- Fixed business dates and explicit expected states make fixtures reproducible; a unique run namespace prevents collisions across executions. The notification reminder period is captured from the persisted scheduler result rather than guessed from the browser clock.
- Browser execution uses two workers with full parallel scheduling and zero automatic retries. Per-test output paths and per-run output directories avoid artifact overwrites.
- `CI_RUN_ID` selects a validated directory under `.verification/`; separate local runs must also select distinct published ports. CI retains the `ci` default. Only a guarded `workforce-ci-<12 hex>` project can be managed. Fixture selection SQL scopes project codes by organization.
- No changes to application business logic, dependencies, migrations or original development environments.

## Verification commands and results

Local commands use the repository-scoped Node 24 runtime and existing Java 21 / Node 24 Docker runtimes; no global toolchain changes. Runtime evidence stays ignored under `.verification/phase12a/`.

| Check | Result | Evidence |
| --- | --- | --- |
| Backend security regression | PASS: 102 tests, zero failures/errors/skips | backend/tests.log and backend/reports |
| Frozen frontend install, lint, typecheck | PASS | frontend-static.log |
| Frontend production build | PASS during isolated Compose build | start.log |
| Standalone onboarding before CI seed | PASS: one test including self-created duplicate slug and recovery | onboarding-ready.log |
| Standalone mobile review before editing test | PASS: one test using its own submitted month | mobile-alone.log |
| CI helper guards / isolation / redaction | PASS: eight tests | helper-tests.log |
| GitHub workflow syntax | PASS: actionlint 1.7.7 | actionlint.log |
| Complete smoke suite | PASS: 26 tests, zero failures/skips | full-browser.log |
| Complete parallel browser suite | PASS: all 13 tests in one execution, 7.1 minutes, two workers, zero retries | full-browser.log |
| Exact Mailpit comparison | PASS: 51 delivered messages across all seven notification types | full-browser.log |
| Isolated Docker stack | PASS: all six services healthy; health/OIDC/OpenAPI/Swagger checks passed; diagnostics saved and only this run's containers/volumes removed afterward | start.log, diagnostics/services.txt, stop.log |
| GitHub-hosted run | Not run: no push authorized | local verification only |

Exact backend command (in a copied backend directory inside Java 21 Docker, separate PostgreSQL Testcontainers):

```sh
./mvnw -B -ntp test -Dtest=PhaseOneIntegrationTest,PhaseNineIsolationTest,PhaseTenOnboardingTest,PhaseElevenAdministrationTest,ExceptionHandlerAdviceTest '-DargLine=-Xmx1536m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1'
```

Frontend commands: `pnpm install --frozen-lockfile`, `pnpm lint`, `pnpm typecheck`, and Dockerfile `pnpm build`. Focused commands: `pnpm test:browser onboarding.spec.ts` and `pnpm test:browser timesheets.spec.ts --grep 'mobile employee'`. Full invocation: `CI_RUN_ID=phase12a CI_BROWSER_CONTAINER=workforce-browser-tests PLAYWRIGHT_WORKERS=2 node infra/ci/stack.mjs test` (PowerShell environment equivalents locally). This runs the unchanged smoke script through Node's test runner, `pnpm test:browser`, then the existing exact Mailpit verifier.

Helper command: `node --test infra/ci/ci.test.mjs`. Infrastructure commands: `CI_RUN_ID=phase12a node infra/ci/stack.mjs prepare`, `config`, `start`, `seed`, `test`, followed by `diagnostics`/`stop` after verification. Run ports are 23000/28080/28180/25432/28025/21025. Compose project: `workforce-ci-9537bce710b0`; original `workforce` and interactive `workforce-ci-98b4339864f0` environments are excluded.

Docker-backed commands required outside-sandbox execution. Original databases/realms were not migrated or reset. A first standalone local browser attempt launched before frontend readiness and failed to connect; bounded readiness polling was added to the ignored local focused runner, after which onboarding passed before any seed. This startup attempt is disclosed separately from the required complete suite run.

Mailpit exact comparisons passed for 14 employee reminders, five manager reminders, two rejections, two approvals, six SAP failure notifications, eight SAP UNKNOWN notifications and 14 finance reminders. Checks cover recipients, subjects, UTF-8 text, links, comments and ordinary deduplication. Ambiguous SMTP acceptance still cannot guarantee exactly-once physical delivery.

No remaining 12A blocker. The full backend suite was not rerun: this checkpoint ran the five relevant security/integration classes listed above. Mock SAP behavior was exercised through the full browser suite; its standalone unit suite was not rerun. Production load, real SAP, production SMTP and GitHub-hosted execution are not claimed by this local checkpoint. Changes remain uncommitted on the requested branch.

## Changed files

- `frontend/tests/browser/onboarding.spec.ts`: self-contained collision fixture, original assertions retained.
- `frontend/tests/browser/fixtures.ts` (new): required private per-spec manifest, no developer-credential fallback.
- `approvals`, `notifications`, `organizations`, `reports`, `sap-worker`, `sap`, `timesheets` browser specs: isolated fixture loading; unique artifact paths; separate mobile month.
- `frontend/playwright.config.ts`: full parallel scheduling, two workers, isolated output paths, zero retries preserved.
- `infra/ci/stack.mjs`, `support.mjs`, `ci.test.mjs`: guarded independent run directories, atomic ownership/consumption, per-spec seed, tenant-scoped SQL, private manifests, short-lived admin token renewal and pre-console redaction.
- `.github/workflows/e2e.yml`: upload the new safe per-run browser-results location; preserve triggers, permissions and cleanup.
- `README.md` and this progress file: usage, evidence and checkpoint boundaries.

## Audit findings reserved for later checkpoints

### 12B — usability and documentation

- Project/employee code links look like ordinary text; assignment and lifecycle actions are far below the list/detail headings. Users confused Archive with Save. Review discoverability and confirmation wording.
- Membership, employee profile linkage, manager relationship and accepted invitation are separate prerequisites. User testing needed manual diagnosis of an unlinked manager and a registered finance account with a pending invitation. Provide clearer setup status and next-step guidance; preserve the security boundaries.
- Personal dashboard versus organization/role-specific work needs review. Do not grant FINANCE/ADMIN manager-review overrides.
- Some documentation still claims no administration API/role endpoint despite Phase 11; update those passages during documentation cleanup, without rewriting historical verification results.

### 12C — security and performance

- Session access-token expiry requires reauthentication; encrypted-cookie session lifetime does not refresh the Keycloak token. Review user messaging and any future refresh design with expiry/logout/security tests.
- Public API docs are intentionally available locally; decide production network exposure. Registration/invitation abuse limits and per-organization quotas need explicit assessment beyond Keycloak login brute-force protection.
- Tenant authorization and composite FKs are implemented; no PostgreSQL RLS defense exists. Any new query/worker must retain explicit organization ownership. Already-authorized in-flight operations cannot be retroactively cancelled by membership changes.
- Reporting downloads are bounded but buffered in memory. No production-volume query plans, load test, concurrent-download memory measurements or tenant fairness test has been performed.
- `ProjectService.assigned` uses `LocalDate.now(ZoneOffset.UTC)` while business calendars use Europe/Athens. Audit the near-midnight assignment/dashboard behavior with an injected Clock regression in 12C; this timezone inconsistency was found by inspection, not changed in 12A.
- SMTP ambiguous acceptance can duplicate physical delivery. SAP requires reconciliation on ambiguous acceptance; preserve immutable snapshots and stable export identity.
- These are review items, not claims of newly reproduced vulnerabilities. Dependency scanning, session hardening and performance changes belong to 12C.

### 12D — production operations

- Compose uses Keycloak `start-dev`, HTTP localhost configuration and a shared local PostgreSQL role for the application/identity databases. Production requires deliberate TLS/issuer/proxy and least-privilege database configuration.
- Backups/restore drills, secret/signing-key rotation, production SMTP, monitoring/alerting and recovery/runbooks remain prerequisites. Real SAP company routing and tenant credentials are not designed or validated.
- Original populated development DB remains V14; any real migration needs a reviewed current ownership mapping and backup. Never infer legacy membership or enable `LEGACY_TENANT_CONFIRMED` without that mapping.
- Render remains an architectural target, not a deployed environment. No provider/infrastructure changes in 12A.

## Next checkpoint

12A is complete; stop here. A later explicitly requested 12B should start from this audit and improve existing screens without changing authorization, tenancy, lifecycle, SAP boundaries or implementing 12C/12D. Preserve the isolated fixture manifest, single-use suite guard and zero-retry full-suite gate. Review the uncommitted 12A changes before starting that checkpoint.
