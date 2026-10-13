# Phase 11 administration verification

Work is on `feature/phase11-administration`, based on the clean Phase 10 merge (`634bedf`). Changes remain uncommitted. No push, merge, deployment, identity-provider replacement or Phase 12 implementation is authorized or performed.

## Implementation

The existing organization, employee and project modules now provide tenant-scoped administration; there is no new service or queue. New screens cover employees and details, members, invitations, projects and details under `/app/admin`. Existing ADMIN project writes are reused and audited; assignments gain an update endpoint. The invitation acceptance pages are outside membership-required `/app`, permitting users who have no organization yet to authenticate and accept securely.

See [administration.md](administration.md) for all API routes, request/version conventions, role rules, account linkage, lifecycle, project-assignment policy and operational setup. The BFF authenticates every administration action; Spring independently checks active organization membership and ADMIN role. FINANCE and ADMIN retain existing reporting/SAP permissions but do not gain manager approval overrides.

New backend files: `AdministrationAccess`, `EmployeeAdministration`, employee administration controller/DTOs, `MembershipAdministration` and controller, `InvitationTokens`, `Invitations` and controller, `ProjectAdminController`, and `PhaseElevenAdministrationTest`. Existing project service/assignment entity/controller, employee entity, security/advice, reporting projections and notification worker/store are extended. Frontend additions are the admin pages/forms/actions/lookups, invitation context/review/accept pages and actions, server view helpers, and administration browser test. Navigation, sign-in callbacks, logout-cookie cleanup and smoke tests are updated. Compose/CI generate a separate invitation signing key; CI diagnostics redact invitation tokens and signing credentials.

## Migration and historical data

Only **V17__organization_administration.sql** is added. V1–V16 are unchanged. Employee account links become optional, employee names can be set before registration, and employee/membership versions are added. Membership status adds REVOKED. Invitations have token hashes, expiry, state constraints, tenant foreign keys and pending uniqueness. Administration audit records are append-only. Notification recipients may be absent only for the new invitation type, with a tenant-enforced invitation foreign key.

The migration was rehearsed using the previously reviewed V14 snapshot in a separate PostgreSQL container on an internal network. Before the acknowledgement was supplied to that copy, all table row counts/hashes matched the recorded baseline and all 73 user IDs/roles matched the reviewed single-organization ownership manifest, with zero unmatched identities. V15–V17 then migrated **1,902 historical application records** without changes to original fields; added columns were excluded from original-field checksums. All 73 memberships remained correctly mapped, no organization-creation receipts were invented, and Hibernate validation/health passed. Evidence: ignored `.verification/phase11-migrate-copy.log`, `.verification/phase11-legacy/result.json` and migration log. The disposable migration containers/network were removed afterward.

This confirms only that reviewed snapshot, not arbitrary future data. No new ownership ambiguity was found in it. The original development database was queried read-only and remains **V14**; original PostgreSQL/Keycloak volumes were not reset or upgraded. `LEGACY_TENANT_CONFIRMED` remains false in the configuration template and was enabled only in the disposable migration-copy environment after mapping verification. A real upgrade still requires the controlled ownership review, backup and realm-update procedure.

## Security and concurrency matrix

| Scenario | Expected / observed result | Evidence |
| --- | --- | --- |
| EMPLOYEE/MANAGER/FINANCE administration reads and mutations | 403; cannot self-promote | PhaseElevenAdministrationTest.onlyAdminCanUseAdministration (three roles) |
| Anonymous administration/acceptance | 401 | anonymousCannotUseAdministrationOrInvitationAcceptance |
| Cross-tenant employee/project/assignment/invitation/member IDs | 404 or membership 403; no foreign data | crossOrganizationGuessesNeverRevealResources, foreignInvitationsMembershipsAndEmployeeManagersAreNotMutable; existing PhaseNineIsolationTest |
| Suspend/reactivate/change role | Subsequent authorization reflects database membership | suspendedMembershipImmediatelyLosesAccessAndCanBeExplicitlyReactivated |
| Last ADMIN demotion/suspension/revocation | 409; active administrator retained | lastAdministratorCannotLoseAccess |
| Concurrent self-demotions | One succeeds, one conflicts; one active ADMIN remains | concurrentAdminDemotionsPreserveOneActiveAdministrator |
| Manager self/cycles | 409; original relation preserved | selfAndIndirectManagerCyclesAreRejected |
| Stale employee version | 409, no silent overwrite | unlinkedEmployeeLifecycleAndReporting |
| Account link boundaries | Active local member only; no reassignment | accountLinkRequiresActiveLocalMemberAndCannotBeReassigned |
| Invitation wrong identity/unverified email/tampered token | Rejected, no grant | wrongOrUnverifiedIdentityCannotAccept |
| Existing membership plus elevated invitation | Conflict, existing role unchanged | existingMembershipCannotBeElevatedByInvitationAndRevocationIsExplicit |
| Repeated/concurrent acceptance | One membership/link/audit event; original subject's replay idempotent | invitationCreatesMembershipAndLinkExactlyOnceWithoutOrganizationHeader, concurrentAcceptanceConsumesOnce |
| Different subject, same verified email after consumption | Conflict, no second membership | consumedInvitationCannotBeUsedByAnotherSubjectWithSameVerifiedEmail |
| Expiry/duplicate pending/revocation | No acceptance; pending uniqueness enforced; revoked delivery cancelled | pendingDuplicateAndExpiredInvitationAreHandledSafely, revokedInvitationIsCancelledBeforeDelivery |
| Hash-only storage / outbox transaction boundary | No token in durable body; SMTP outside DB transaction; one ordinary delivery | emailUsesDurableWorkerWithoutPersistingTokenOrHoldingTransaction |
| Admin audit immutability and invitation filtering | Database rejects mutation; correct filtered records | invitationStatusFilterAndAuditImmutability |
| New unlinked profiles and reports | Included with profile name; tenant uniqueness and pagination | unlinkedEmployeeLifecycleAndReporting, scopedNumbersSearchAndPagination |
| Assignments/project archival/history | Existing eligibility enforced; historical entries retained | assignmentLifecycleAndProjectArchivePreserveEligibility |

All these backend cases passed in the final full run. Existing timesheet, approval, SAP, notification, reporting, tenant isolation and onboarding assertions remain intact. The old onboarding replay fixture now adds a backup ADMIN before suspending the creator, preserving its original security assertion while satisfying V17's new invariant.

## Verification commands and observed results

| Check | Observed result |
| --- | --- |
| Java 21 full Testcontainers suite | **381 passed, 0 failures, 0 errors, 0 skipped**, including 26 Phase 11 cases |
| Production Maven package | PASS |
| Node 24 frozen install, lint, typecheck, build | PASS; final production frontend rebuilt successfully after mobile wrapping changes; final lint/typecheck also passed |
| Mock SAP | **7 passed**, no failures/skips |
| CI helper security/readiness tests | **5 passed** |
| GitHub workflow actionlint | PASS |
| Six-service Compose / OIDC / health / OpenAPI / Swagger | PASS, including final rebuild; original six services preserved |
| Administration invitation browser workflow | **1 passed** in the corrected focused rerun (2.1 minutes) |
| Existing browser regressions | **12 passed** in the complete browser run; that run's new administration test had a navigation-selector defect, corrected and verified separately |
| Smoke | **26 passed**, no failures/skips |
| Exact Mailpit business-message verification | **26 messages passed** across all seven existing types; invitation/Keycloak email checks passed separately in the administration flow |
| GitHub-hosted execution | NOT RUN; changes are uncommitted |

Exact commands (inside the respective source directory):

```sh
# backend, Maven Wrapper / Java 21 / PostgreSQL Testcontainers
./mvnw -B -ntp test -Dtest=PhaseElevenAdministrationTest '-DargLine=-Xmx1536m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1'
./mvnw -B -ntp test '-DargLine=-Xmx1536m -XX:ActiveProcessorCount=2 -Dspring.test.context.cache.maxSize=1'
./mvnw -B -ntp clean package -DskipTests
# frontend, Node 24 / repository pnpm 12.8.1
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm build
pnpm exec playwright test administration.spec.ts
pnpm test:smoke
pnpm test:browser
# root, scoped Node 24
node --test mock-sap/server.test.mjs
node --test infra/ci/ci.test.mjs
node infra/ci/stack.mjs prepare
node infra/ci/stack.mjs config
node infra/ci/stack.mjs start
node infra/ci/stack.mjs seed
node infra/ci/stack.mjs test
node .verification/mailpit11.mjs
node infra/ci/stack.mjs diagnostics
node infra/ci/stack.mjs stop
```

The stack helper invokes `docker compose config --quiet` and `docker compose up --build -d --wait --wait-timeout 600`. Verification project: `workforce-ci-98b4339864f0`, separate ports 13000/18080/18180/15432/18025/11025. Synthetic secrets remain in ignored state files. Docker-backed Java, frontend, Testcontainers, Playwright, migration-copy and Compose commands require outside-sandbox execution on this Windows machine. Mock and helper tests use the scoped Node 24 binary. No global toolchain was modified.

Locally, the stack helper uses the scoped Node test runner for the exact `test:smoke` script and `CI_BROWSER_CONTAINER=workforce-browser-tests` for `pnpm test:browser`. Mailpit's final check was run separately because the complete browser command exited nonzero for the subsequently corrected new-test selector. The final result is **13 distinct browser scenarios passed across the complete-regression and focused-correction runs**, not a claim that the initial combined command was green. No test was skipped or weakened. The focused corrections wait for the actual detail URL and select the actual invitation error rather than Next.js's route announcer.

Evidence (ignored local files): `backend11/full.log`, `backend11/full-reports/surefire-reports/` (381 final test cases), `backend11/package.log`, `frontend11-final.log`, `frontend11-static-final.log`, `frontend11-wrap-build.log`, `phase11-stack-final.log`, `phase11-browser-final.log` (12 existing scenarios), `phase11-admin-final.log` (corrected new scenario), `phase11-mailpit.log`, `phase11-migrate-copy.log`. Earlier failed reports remain preserved separately; root-level `backend11/full-reports/TEST-*.xml` belong to the initial run, not the final nested report set.

The new browser scenario registers the organization ADMIN through Keycloak and populates a genuinely empty organization through UI actions. It creates manager/employee profiles, assigns the reporting relation, delivers three role-specific invitations through Mailpit, accepts with newly registered manager/employee accounts and a previously registered FINANCE account, creates a project and dated assignment, records/submits time, approves as the direct manager, exports successfully as FINANCE, and confirms SAP Excel availability. It checks last-admin rejection, employee activation/deactivation, project archival with preserved approved history, mobile list/detail layout, role-specific navigation and logout, plus denial when a different identity opens an already-consumed invitation.

Existing browser coverage passed for rejection/reopening/resubmission, immutable decisions, SAP transient failures/manual retry/UNKNOWN reconciliation, SAP XLSX, report filters and CSV/XLSX, notification privacy, organization switching, registration/recovery, optimistic timesheet conflicts, mobile navigation and logout. Exact Mailpit business totals: 2 employee reminders, 4 manager approval reminders, 2 rejections, 2 approvals, 6 terminal SAP failures, 8 UNKNOWN messages and 2 Finance reminders. Recipient, subject, UTF-8 body, links, comments and ordinary deduplication matched persisted intent.

## Corrections and limits

- Initial frontend lint rejected synchronous state setting and a render-time clock call in invitation pages; these were corrected without rule suppression.
- The first full backend run found that the earlier onboarding replay fixture attempted to suspend its only ADMIN. The fixture now retains a backup administrator; all replay-denial assertions remain.
- Browser selectors now distinguish the organization-role select from the access-confirmation label and the invitation error from Next.js's route announcer. The test waits for detail navigation before capturing its UUID.
- Browser testing reproduced logout returning Forbidden after invitation acceptance. A `no-referrer` policy suppressed the Origin needed by the existing POST logout CSRF check. Invitation pages now use `same-origin`; tokens remain absent from referrer URLs, and the CSRF check is unchanged. The corrected full administration flow confirms successful logout afterward, including mobile and the wrong-account invitation error page.
- Employee/project detail headings and account text wrap within mobile widths; browser assertions cover both detail pages and the employee list.

No new process-kill recovery experiment is claimed. Existing durable-worker, expired-claim, fencing and retry regressions are included in the full backend suite; the invitation test checks ordinary deduplication and transaction boundaries. SMTP ambiguous acceptance may still duplicate physical email delivery; atomic invitation consumption does not imply exactly-once SMTP.

Read [administration.md](administration.md) for key rotation, pending-invitation lifecycle, bounded selectors, employee-versus-membership status and access changes to managers. Future production use requires reviewed identity/SMTP configuration, signing-key management and a controlled legacy upgrade. Local Phase 11 verification is complete; a GitHub-hosted run still requires an authorized commit/push. No unresolved critical or high-severity defect was identified by these checks. Any Phase 12 scope requires a new explicit task. Changes remain uncommitted for review.

Final cleanup: redacted diagnostics captured all six isolated services healthy. The guarded CI helper removed only `workforce-ci-98b4339864f0` and its synthetic volumes/network. A subsequent read-only check confirmed all six original `workforce-*` development services healthy and the original database still at V14. Final `git diff --check` passed. Working tree: **25 modified tracked files and 37 new files**, uncommitted on `feature/phase11-administration`. No old migration, dependency version or workflow definition was rewritten.
