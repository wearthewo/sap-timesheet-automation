# Phase 10: self-service registration and organization onboarding

Implementation and local verification **complete** on `feature/phase10-registration-onboarding`, 2026-10-13. Changes remain uncommitted for review. No deployment, branch push/merge, original development database migration or original Keycloak realm update was performed.

## Registration and authentication

On `/login`, **Create an account** begins Keycloak registration using the confidential Workforce client, authorization code flow, PKCE S256 and state. The additional exact callback is `${FRONTEND_URL}/api/auth/callback/keycloak-register`; the existing sign-in callback remains unchanged. NextAuth keeps access tokens inside its encrypted HttpOnly session; browser-visible session data does not contain them. Callback destinations are fixed application paths, using NextAuth's same-origin redirect handling.

Fresh realm imports enable self-registration, email verification, password recovery, minimum password length 12, username/email exclusion and brute-force protection. Credentials remain in Keycloak. See the [Keycloak administration guide](https://www.keycloak.org/docs/26.7.0/server_admin/index.html) for verification, password and SMTP settings. Local identity emails go to Mailpit, using `KEYCLOAK_SMTP_HOST`, `KEYCLOAK_SMTP_PORT`, `KEYCLOAK_SMTP_FROM`, `KEYCLOAK_SMTP_AUTH`, `KEYCLOAK_SMTP_STARTTLS`, `KEYCLOAK_SMTP_SSL`, `KEYCLOAK_SMTP_USERNAME` and `KEYCLOAK_SMTP_PASSWORD`. These settings are distinct from Workforce's business notification SMTP settings/outbox.

The configured Keycloak 26.8 registration flow collects the profile first, verifies the email through Mailpit, then collects the password as a required action before completing OIDC sign-in. The browser test follows those real pages and the actual verification/recovery email links; it does not mark the registration account verified through the admin API.

An internal identity is provisioned idempotently by validated Keycloak `sub`; PostgreSQL's unique external-subject constraint and `ON CONFLICT DO NOTHING` handle concurrent first requests. Matching emails never merge users. Existing identities, display names and memberships are preserved. Missing name claims fall back to preferred username, then `Employee`. Registration grants no global ADMIN role; the deprecated global field remains EMPLOYEE for newly provisioned identities.

Spring requires **boolean** `email_verified=true` from the validated token for organization creation and all organization-owned business endpoints. Missing, false and string claims do not qualify. `/auth/me` without organization context supports verification guidance; `/auth/organizations` returns no memberships for an unverified token. A stale unverified token requires signing out and back in after verification. Membership roles remain exclusively database-owned.

## Organization API and transaction

`POST /api/v1/organizations`, authenticated and email-verified, with UUID `Idempotency-Key` header:

```json
{"name":"Example Company","slug":"example-company"}
```

Response: HTTP 201 with `id`, `slug`, `name`, `role: "ADMIN"`. Repeating the same request key and normalized fields returns that same organization. The request key is scoped to the authenticated user. Reusing it with different fields returns `IDEMPOTENCY_CONFLICT` (409). Unknown request fields, including owner, role, organization ID or membership state, are rejected (400).

Names are stripped, 1–200 characters, without control characters. Slugs are stripped/lowercased, 3–63 ASCII letters/digits with single internal hyphens; reserved infrastructure/application names are rejected. Duplicate normalized slugs return `ORGANIZATION_SLUG_TAKEN` (409). No duplicate-slug path grants membership in an existing organization.

One short database transaction locks the creator's app-user row, checks its durable idempotency receipt, inserts organization, inserts active ADMIN membership and records the receipt. Concurrent submissions for one identity serialize; cross-identity slug collisions are protected by the unique database index. A failed membership insert rolls back the organization. Replays verify the original organization's active ADMIN membership and cannot restore suspended/revoked access. No network call occurs inside this transaction.

## Migration and existing data

Only **V16__organization_creation_requests.sql** is added. V1–V15 are unchanged. V16 adds `uq_organization_normalized_slug` on `lower(btrim(slug))` and the durable `organization_creation_requests` table, with UUID user/organization references and a composite membership FK. It does not rewrite any existing row or create memberships for existing users.

If an operator previously created slugs that collide after trimming/lowercasing, index creation fails safely. Review such collisions rather than silently renaming or merging organizations. Suggested preflight:

```sql
SELECT lower(btrim(slug)),count(*) FROM organizations GROUP BY lower(btrim(slug)) HAVING count(*)>1;
```

The original development database remains at V14. Its reviewed Phase 9 ownership mapping and [populated-copy rehearsal](phase9-legacy-migration-rehearsal.md) remain intact. An eventual upgrade still requires a fresh backup, current ownership review and the explicit V15 acknowledgement. Clean Testcontainers success does not replace populated-data migration review. Nothing here resets PostgreSQL or Keycloak volumes or automatically sets the original environment's acknowledgement.

The Phase 10 rehearsal restored the previously reviewed populated V14 dump into a separate disposable PostgreSQL instance. It verified the original manifest and ownership mapping before enabling the acknowledgement **on that copy only**, then applied V15 and V16. All **1,902 application records** retained identical hashes over their original fields, all **73 mapped memberships** were present, no organization-creation receipts were invented, and Hibernate schema validation passed. Evidence is in ignored `.verification/phase10-legacy/result.json` and `migration.log`. The temporary copy was removed; the original database and original Keycloak realm were not changed. This proves preservation for that reviewed snapshot, not ownership of subsequently added development data.

## Existing Keycloak realms

Editing the import JSON does **not** update an imported realm. The controlled helper defaults to a review-only operation:

```sh
node infra/keycloak/update-registration.mjs --env-file .env
# Review targeted settings, preserve a configuration backup, then explicitly apply:
node infra/keycloak/update-registration.mjs --env-file .env --apply
```

This local-only helper authenticates with the operator's local admin credentials, changes only registration/security/SMTP settings and adds the exact registration callback to the existing client. It requires confidential code flow/PKCE and disabled direct-password/service-account grants. It does not import/replace the realm, update users or rotate the client secret. Repeated application is safe. It never marks old users verified; they must verify through Keycloak. The original realm was not updated during implementation.

The isolated CI helper checks a fresh realm and creates a separate temporary old-settings realm, preserves a user, runs review-only then two applies, checks unchanged user data/client secret and removes only that temporary realm. Normal registration and organization creation never call the Keycloak admin API.

## Frontend states

- Anonymous visitors sign in/register through Keycloak.
- Unverified authenticated users see email-verification guidance and sign-out.
- No active memberships (including suspended-only) leads to `/onboarding`, with a creation form and explanation that creating an independent organization does not restore suspended access.
- One active membership is selected as before; multiple memberships use the existing selector. Stale selection returns to the selector. Spring revalidates access independently.
- Successful creation sets the HttpOnly/SameSite organization cookie and opens an empty organization workspace. No employees/projects/timesheets are seeded. Without an employee profile, the dashboard explains setup instead of offering a personal current-month card.
- The form preserves inputs after errors, announces validation/service errors, disables duplicate clicks while pending and retains its request key across ambiguous network failures. Reload the form to start a different request if the previous key was already committed.

Server Actions use the existing authenticated BFF and Next.js origin/CSRF protections. No bearer tokens or Keycloak administrator credentials are exposed to browser JavaScript. Logout remains available on onboarding and mobile navigation.

## Verification evidence

Local results on 2026-10-13:

| Check | Observed result |
| --- | --- |
| Full Maven/Testcontainers suite | 355 passed, 0 failures/errors/skips |
| Included PhaseTenOnboardingTest | 29 passed, including deterministic concurrent requests and rollback |
| Production backend package | BUILD SUCCESS |
| Frozen frontend install, lint, typecheck, production build | PASS |
| Production frontend smoke tests | 21 passed, 0 skipped |
| Focused real registration/onboarding/recovery Playwright flow | 1 passed |
| Full existing/new Playwright suite | 12 passed in 7.2 minutes, 0 failures/retries/skips |
| Mock SAP Node tests | 7 passed |
| CI helper tests | 5 passed, including OAuth/JWE/cookie diagnostic redaction |
| Populated V14 → V16 copy | PASS: 1,902 preserved records, 73 memberships |
| Fresh realm and controlled existing-realm upgrade | PASS; existing user/client-secret preservation checked across two applies |
| Exact Mailpit business-message verification | 26 messages matched: recipients, subjects, UTF-8 bodies, links, comments and ordinary deduplication |
| Docker Compose | Config/build/start PASS; all six services healthy; health/OIDC/OpenAPI/Swagger HTTP 200 |
| Workflow validation | actionlint 1.7.7 PASS; GitHub-hosted runs not executed for these uncommitted changes |

The security suite covers authentication, strict boolean verification claims, spoofed fields, normalization/reserved slugs, concurrency, atomic rollback, idempotency, suspension, shared-email identity separation, empty workspace and cross-tenant restrictions. Existing tests retain their assertions with explicit verified-email fixture claims.

| Security scenario | Expected and observed | Test location |
| --- | --- | --- |
| Anonymous or unverified creation/business access | 401/403; no organization inserted | `PhaseTenOnboardingTest` |
| Client-supplied owner/role/status | 400; no privilege or ownership accepted | `PhaseTenOnboardingTest` parameterized cases |
| Concurrent same-key creation | Exactly one organization, one ADMIN membership and one receipt | `PhaseTenOnboardingTest.concurrentRepeatedSubmissionCreatesOnlyOneOrganization` |
| Concurrent cross-user slug collision | One winner; losing identity gains no membership | `PhaseTenOnboardingTest.concurrentSlugCollisionHasOneWinner` |
| Membership persistence failure | Organization transaction rolls back | `PhaseTenOnboardingTest.failedMembershipInsertRollsBackOrganization` |
| Replayed creation after suspension | Access denied; status not reactivated | `PhaseTenOnboardingTest.suspendedMembershipAndOrganizationAreNotReactivatedByReplay` |
| Different subjects sharing an email | Separate internal identities | `PhaseTenOnboardingTest.provisioningIsIdempotentAndSharedEmailDoesNotMergeSubjects` |
| Newly created ADMIN guesses another tenant's resources | Denied; existing tenant isolation preserved | `PhaseTenOnboardingTest.newOrganizationCannotAccessOtherOrganizationsData`, `PhaseNineIsolationTest` |
| Verification/recovery links and session cookies | Server-side OIDC exchange; browser session excludes access token; logout clears selected organization | `frontend/tests/browser/onboarding.spec.ts`, `infra/ci/ci.test.mjs` |

Exact commands (repository subdirectories as shown):

```sh
# backend, Maven Wrapper in Java 21 container; PostgreSQL Testcontainers via Docker socket
./mvnw -B -ntp test -Dtest=PhaseTenOnboardingTest '-DargLine=-Xmx1536m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1'
./mvnw -B -ntp test '-DargLine=-Xmx1536m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1'
./mvnw -B -ntp clean package -DskipTests
# frontend, Node 24 / pnpm 12.8.1 scoped container
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm build
node ../infra/ci/frontend-smoke.mjs
pnpm exec playwright test onboarding.spec.ts
# repository root, scoped Node 24
node --test mock-sap/server.test.mjs
node --test infra/ci/ci.test.mjs
node --check infra/keycloak/update-registration.mjs
node --check infra/ci/registration-realm.mjs
node infra/ci/stack.mjs prepare
node infra/ci/stack.mjs config
node infra/ci/stack.mjs start
node infra/ci/stack.mjs seed
node infra/ci/stack.mjs test
```

The stack helper invokes `docker compose config --quiet`, `up --build -d --wait --wait-timeout 600`, smoke/browser scripts and the exact Mailpit business-message checker. Local ports are 13000/18080/18180/15432/18025/11025; generated credentials remain in ignored `.verification/ci`. GitHub runs the same helper with native Node/Playwright. Local browser tests use `CI_BROWSER_CONTAINER=workforce-browser-tests`; the host smoke command is Node's test runner for the exact `test:smoke` script. Docker-backed backend, frontend, Testcontainers, browser, populated-copy and Compose commands required outside-sandbox execution. No global Java/Node installation was modified.

Corrections found during verification: the onboarding loading boundary initially streamed an anonymous redirect with HTTP 200; moving the identity guard into its parent layout restored the expected protected-route redirect. The registration test was corrected to follow Keycloak's separate password step and distinguish the form error from Next.js's route announcer. Its session assertion now uses browser-origin fetch so the local Docker hostname mapping applies. Diagnostic redaction now covers encrypted session tokens and chunked authentication cookies as well as verification/reset URLs. The isolated seed helper waits for backend readiness before inserting fixtures. An interrupted overnight run crossed business dates, failed its single-reminder count assertion and had one login/navigation timeout; its results are not treated as a passing suite and no definitive cause is claimed for the login timeout. The fresh isolated rerun passed all original assertions without retries or increased timeouts.

The final business run used disposable project `workforce-ci-24088f1ccfa8`. Evidence is in ignored `.verification/phase10-fresh-{start,seed,browser,stop}.log`, `frontend10-final.log`, `backend10/release-{test,package}.log`, the preserved Surefire reports, and redacted `.verification/ci/diagnostics`. Business email counts were: 2 timesheet reminders, 4 approval reminders, 2 rejection decisions, 2 approval decisions, 6 terminal SAP failures, 8 UNKNOWN events and 2 finance monthly reminders. Keycloak verification and recovery messages were separately checked by the registration test using the actual recipient and secret action link without logging that link. The final SELECT-only check of the original database returned Flyway **14**.

After verification, `node infra/ci/stack.mjs diagnostics` saved redacted diagnostics and `node infra/ci/stack.mjs stop` removed only the disposable CI project and its synthetic volumes. All six original development services remained healthy. Final lint/typecheck after the browser-test adjustment also exited 0. `git diff --check` passed. Final branch: `feature/phase10-registration-onboarding`; 29 tracked files modified and 16 new files, all uncommitted. No push, merge or deployment was performed.

## Files added or changed

- Backend: `identity/application/VerifiedIdentity.java`, `organization/api/OrganizationController.java`, `organization/application/OrganizationCreation.java`, V16, `IdentityController`, `SecurityConfiguration`, and `PhaseTenOnboardingTest`. Existing Phase 1–9 test fixtures now explicitly supply verified-email JWT claims; their business/security assertions remain intact.
- Frontend: `/onboarding` page/layout/loading/error/form/action files, shared organization-cookie helper, login registration control, organization selector, empty-workspace dashboard, server-side authentication/backend helpers, smoke tests and the new registration browser specification.
- Local infrastructure: realm import, controlled `infra/keycloak/update-registration.mjs`, isolated `infra/ci/registration-realm.mjs`, CI seed readiness and diagnostic redaction/tests, Compose SMTP variables and `.env.example`.
- Documentation: README, architecture, security and this verification report. V1–V15, dependency versions, workflow definitions and AGENTS rules remain unchanged.

## Scope and deployment prerequisites

No invitations, employee/project administration UI, paid services, production SMTP/SAP, infrastructure deployment or Phase 11 implementation. Trusted operator maintenance remains necessary for employee/project setup until Phase 11. Production registration requires a managed HTTPS Keycloak deployment, verified sender/SMTP transport and secrets, exact public URLs, policy review and abuse/rate-limit planning. Local Mailpit and Keycloak `start-dev` are not production delivery/authentication infrastructure. GitHub results are not claimed for uncommitted changes. Business SMTP ambiguous acceptance can still cause duplicate physical delivery; this phase does not change that limitation.
