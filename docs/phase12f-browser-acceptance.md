# Phase 12F — real-browser functional acceptance

For click-by-click instructions, roles, troubleshooting and additional manual variants, see the [complete local browser testing guide](local-browser-testing-guide.md).

## Decision

**25 October 2026: GO for the covered local customer workflows.** Checkpoints A–E are complete for the critical onboarding, people-management, employee/manager/finance, tenant-isolation and mobile/logout paths below. No unresolved critical authorization, data-loss or workflow defect was found in the executed scenarios. This is not production-security clearance or deployment approval. Two additional edge cases remain explicitly NOT TESTED.

**56/56 exploratory browser scenarios passed:** A:12, B:9, C:18, D:5, F:4, H:3, R:5. A separate durable administration Playwright scenario passed **1/1, one worker, zero retries, 2.7 minutes**. Thus 57 browser scenario executions have passing final outcomes, zero unresolved FAIL/BLOCKED outcomes, and two listed NOT TESTED cases. The durable scenario overlaps exploratory coverage; this does not mean 57 distinct capabilities. Assertions, screenshots, harness corrections and reruns are not additional scenarios.

Phase 13 preparation remains paused. Its preexisting uncommitted infrastructure changes are preserved separately. No push, merge, publication, cloud operation or deployment occurred. The subsequent user-authorized local checkpoint contains only the Phase 12F files listed below.

## Environment and method

- Application `http://localhost:33000`; Mailpit `http://localhost:38025`.
- Existing Compose project `workforce-ci-493efe29fd43`: frontend, backend, PostgreSQL, Keycloak, Mock SAP, Mailpit. All six healthy at final inspection.
- Branch `feature/phase12-production-readiness`; acceptance-run baseline HEAD `444f4b4306fc212ca6bb0567c1f68df15ac836d6`. The reviewed Phase 12F changes are recorded in the local checkpoint containing this report.
- Actual Chromium 1243, Playwright 1.63.0 and scoped Node 24.22.1 on Windows. Desktop 1440×1000; mobile 375×812. Mobile means browser viewport, not physical-device coverage.
- Synthetic organizations `Phase12F 93017a65a2` and `Phase12F Beta 93017a65a2`; ADMIN, EMPLOYEE, two MANAGER accounts and FINANCE onboarded through real pages. The durable test creates an independent organization/account set.
- No reset, SQL business-data mutation, fixture API write, volume deletion or realm reset. Business setup/mutations used the UI. Mailpit API reads retrieved real email links/content. A UI request was inspected to identify a manager's own timesheet for a self-review URL check; no API mutation replaced a user action.
- Only backend/frontend were replaced with candidate builds. Other QA services and unrelated containers/volumes remained intact.

Private evidence is under ignored `.verification/phase12f/`: screenshots, results ledger, downloaded workbooks, build/test logs and XML. `state.json` contains credentials/invitation links: **never commit it**. The exploratory driver is `browser-session.mjs`; the persistent interactive session used the locator/navigation corrections documented below. The durable source is `frontend/tests/browser/administration.spec.ts`. No authentication traces were captured or committed; live DOM and request inspection supplied diagnostics.

| Service | Candidate | Local image ID |
| --- | --- | --- |
| Backend | `workforce-backend:phase12f` | `sha256:939e08ce224961b0e71fc2bb1215b5fd3704d69219c5f9f81cce5220664b1fcb` |
| Frontend, final | `workforce-frontend:phase12f` | `sha256:e6326259263d0e1955f22b545f69bc43968438a5544e9118246d7c965e62cc0f` |

The backend uses the verified Java 21/Boot 4.1.2 JAR and existing QA runtime base. The frontend uses the pinned Node 24.22.1 production Dockerfile. Its local QA override starts `node server.js` with the existing environment; this does not verify production secret provisioning.

## Browser matrix

Shared preconditions: A uses new organizations/invited recipients; B uses linked F12-E1 and valid assignments; C changes only those synthetic memberships/profiles or explicitly switches organizations; D uses authenticated roles; F starts from UI-created, manager-approved months; H inspects messages produced by B/F; R repeats problems against the repaired candidate. Each row records actual actions and observed results. Expected outcomes are the authorized operation succeeding, or the stated invalid operation being blocked without corrupting saved data. Reload/new-session checks are specified where performed.

| ID | Role | Expected | Actual browser actions/result | Status | Evidence |
| --- | --- | --- | --- | --- | --- |
| A01 | ADMIN / invited account | Verified creator becomes organization ADMIN | Real registration, verified email, fresh organization and ADMIN dashboard | PASS | A01-admin-dashboard.png |
| A02 | ADMIN / invited account | Membership-only MANAGER can join | MANAGER invited without employee profile; email registration and acceptance completed | PASS | A02-manager-member-no-profile.png |
| A03 | ADMIN / invited account | Profile invitation links the verified employee | EMPLOYEE profile invitation accepted through real email registration | PASS | Interactive results ledger |
| A04 | ADMIN / invited account | Explicit manager linkage persists | Manager account linked explicitly and persisted after reload; initial refresh observation not reproduced with second independent link (OBS02). | PASS | A04-manager-linked.png |
| A05 | ADMIN / invited account | Invited FINANCE gains Finance workspace | Finance invitation accepted by newly registered, verified account; Finance workspace visible. | PASS | Interactive results ledger |
| A06 | ADMIN / invited account | Assigned active projects appear to employee | Two projects created and assigned through Admin; both visible in Employee projects. | PASS | Interactive results ledger |
| A07 | ADMIN / invited account | Second manager account linkage refreshes correctly | Second manager registered via invitation, profile explicitly linked; account state visible without reload. | PASS | Interactive results ledger |
| A08 | ADMIN / invited account | Duplicate invitation blocked; unused invitation revoked | Duplicate pending invitation rejected; unused invitation revoked in UI and persists after reload. | PASS | Interactive results ledger |
| A09 | ADMIN / invited account | Accepted replay creates no duplicate membership | Accepted invitation reopened and accepted idempotently; organization member appears exactly once. | PASS | Interactive results ledger |
| A10 | ADMIN / invited account | Archived/inactive choices excluded until reactivated | Project archive/reactivation and assignment deactivate/reactivate change employee choices after reload while preserving history. | PASS | Interactive results ledger |
| A11 | ADMIN / invited account | Invalid tokens grant no access | Malformed and correctly shaped nonexistent invitation tokens opened in authenticated browser show safe errors and no acceptance action. | PASS | Interactive results ledger |
| A12 | ADMIN / invited account | Filters narrow correct records and survive reload | Member email/status and employee number filters narrow actual Admin lists; member filter persists after reload and employee search submits with keyboard Enter. | PASS | Interactive results ledger |
| B01 | EMPLOYEE | Entry CRUD preserves decimal daily/month totals | Employee created draft, added two projects, edited and deleted entries; daily/monthly 7.25 persisted after reload. | PASS | B01-draft-totals.png |
| B02 | EMPLOYEE | Daily maximum cannot be exceeded | Excess daily hours rejected visibly; saved total remains 7.25. | PASS | Interactive results ledger |
| B03 | EMPLOYEE | Submission makes employee month read-only | Employee submitted draft; pending review is read-only. | PASS | B03-pending.png |
| B04 | MANAGER | Linked direct manager can reject with comment | Explicitly linked invited manager sees existing employee pending sheet and rejects with Greek/HTML-like comment. | PASS | B04-manager-rejection.png |
| B05 | EMPLOYEE / MANAGER | Reopen/correct/resubmit/approve preserves decision history | Employee sees rejection, reopens, corrects to 8 hours, resubmits; manager approves; employee approved page read-only with prior decision retained. | PASS | B05-approved-history.png |
| B06 | FINANCE | Approved export succeeds once | Invited Finance queued approved timesheet, worker reached SUCCEEDED; duplicate export action removed. | PASS | B06-sap-success.png |
| B07 | FINANCE | Downloaded SAP workbook matches exported data | Finance downloaded SAP Excel from actual export detail; safe filename, two expected sheets, numeric 6.25 + 1.75 = 8.00 and no formulas verified in the saved workbook. | PASS | sap-export.xlsx |
| B08 | FINANCE | Reports/downloads contain correct hours | Finance monthly report shows 8 approved and 8 SAP-exported hours; real CSV and Excel downloads saved. | PASS | report.csv; report.xlsx |
| B09 | EMPLOYEE | Date/assignment limits prevent invalid entry | Before assignment validity there are no selectable projects and saving is disabled; out-of-month work date fails native date validity without changing total. | PASS | Interactive results ledger |
| C01 | ADMIN / affected role | Reassignment moves pending approval access | Pending employee reassigned from Manager One to Two; original queue empty, new queue contains sheet. | PASS | C01-reassigned-queue.png |
| C02 | ADMIN / affected role | Suspension blocks; reactivation restores access | Admin suspended Manager Two: organization access denied. Reactivation restores pending queue. | PASS | Interactive results ledger |
| C03 | ADMIN / affected role | Revocation removes organization access | Revoked manager loses organization access; administrator can explicitly restore membership. | PASS | Interactive results ledger |
| C04 | ADMIN / affected role | Role change removes old Finance privileges | Finance demoted to Employee loses report navigation and direct report access; restored Finance role regains navigation. | PASS | Interactive results ledger |
| C05 | ADMIN / affected role | Final active administrator cannot be removed | Attempt to revoke final active ADMIN blocked with helpful error; ACTIVE status persists after reload. | PASS | Interactive results ledger |
| C06 | ADMIN / affected role | One identity can have separate tenant roles | Existing identity invited to Beta; FINANCE there sees empty report, switching to original organization restores EMPLOYEE permissions and approved sheet. | PASS | C06-beta-empty-finance.png |
| C07 | ADMIN / affected role | Cross-tenant employee identifier unavailable | ADMIN in Beta cannot open original-organization employee UUID; switching to authorized original org restores access. | PASS | Interactive results ledger |
| C08 | ADMIN / affected role | Inactive employee cannot create new draft | Deactivated employee cannot create a draft; reactivation restores draft creation and preserves approved history. | PASS | Interactive results ledger |
| C09 | ADMIN / affected role | Employee cannot manage themselves | Manager self-assignment rejected; original empty manager remains after reload. | PASS | Interactive results ledger |
| C10 | ADMIN / affected role | Unauthorized routes disclose no protected data/actions | Direct page access denies Employee/Manager Finance and SAP, all non-admin administration, Employee/Finance approvals, and unrelated manager review. | PASS | Interactive results ledger |
| C11 | ADMIN / affected role | Reporting cycles rejected atomically | Reporting cycle rejected with safe error and unchanged saved manager; temporary relationship removed through UI. | PASS | Interactive results ledger |
| C12 | ADMIN / affected role | Removing manager removes approval access | Removing a manager persists and removes pending approval access; reassignment restores the correct queue. | PASS | Interactive results ledger |
| C13 | ADMIN / affected role | Revocation of one tenant preserves only independent access | Revoked employee cannot open original organization history; selector retains only independent Beta membership. Explicit reactivation restores original approved history. | PASS | Interactive results ledger |
| C14 | ADMIN / affected role | Inactive manager profile cannot review | Inactive manager profile cannot access approvals or be saved as manager; reactivation restores existing direct-report queue. | PASS | Interactive results ledger |
| C15 | ADMIN / affected role | Account already linked cannot be chosen again | Member lookup excludes an account already linked to another profile; unused candidate profile removed safely through UI. | PASS | Interactive results ledger |
| C16 | MANAGER | Manager cannot review own timesheet | Manager creates and submits own hours through UI but direct self-review URL denies approval; a profile without a manager may submit and waits for later assignment. | PASS | Interactive results ledger |
| C17 | EMPLOYEE | Self-service month remains owner-specific | Employee month route retains own approved 8 hours and never exposes other account same-month pending 1 hour. | PASS | Interactive results ledger |
| C18 | ADMIN / affected role | Tenant-specific SAP and manager lookups | Beta Finance sees empty SAP worklist and cannot open original export/download; Beta Admin manager lookup excludes original organization profiles. | PASS | Interactive results ledger |
| D-admin | ADMIN | ADMIN mobile navigation and both viewport logouts | Mobile navigation without page overflow; mobile and desktop Keycloak logout deny protected app until fresh login (admin). | PASS | D-admin-mobile.png |
| D-admin-forms | ADMIN | Admin forms/actions usable at375px | All four Admin sections and employee detail are reachable and contained at 375px; saved manager/profile actions remain available. | PASS | D-admin-employee-mobile.png |
| D-employee | EMPLOYEE | EMPLOYEE mobile navigation and both viewport logouts | Mobile navigation without page overflow; mobile and desktop Keycloak logout deny protected app until fresh login (employee). | PASS | D-employee-mobile.png |
| D-finance | FINANCE | FINANCE mobile navigation and both viewport logouts | Mobile navigation without page overflow; mobile and desktop Keycloak logout deny protected app until fresh login (finance). | PASS | D-finance-mobile.png |
| D-manager2 | MANAGER | MANAGER mobile navigation and both viewport logouts | Mobile navigation without page overflow; mobile and desktop Keycloak logout deny protected app until fresh login (manager2). | PASS | D-manager2-mobile.png |
| F01 | FINANCE (employee/manager prerequisites) | Validation failure terminal and safely displayed | UI-created approved MOCK-INVALID timesheet reaches safe terminal FAILED, no retry action; actual Mock SAP HTTP path. | PASS | F01-validation-failure.png |
| F02 | FINANCE (employee/manager prerequisites) | Manual eligible retry reuses export and succeeds | Mock transient 503 exhausted four attempts; Finance manually retries and fifth succeeds, with operation history visible. | PASS | F02-retry-eligible.png |
| F03 | FINANCE (employee/manager prerequisites) | UNKNOWN reconciles existing record without second export | Timeout-after-create visibly UNKNOWN; Finance triggers reconciliation; existing remote export becomes SUCCEEDED with one export attempt and preserved UNKNOWN history. | PASS | F03-unknown.png; F03-reconciled.png |
| F04 | FINANCE (employee/manager prerequisites) | Report form filters select correct decimal totals | Finance month/employee/project/hour-mode filters submit and persist after reload; July SAP-exported project and employee totals are 1.25. | PASS | Interactive results ledger |
| H01 | EMPLOYEE | Employee messages match actual decisions and recipient | Employee notification history and actual Mailpit approval/rejection messages verified for recipient, period and verbatim Greek/HTML-like rejection comment. | PASS | H01-notifications.png |
| H02 | Member across organizations / FINANCE / ADMIN | Notification history separated by tenant | Same identity sees no notification history in Beta and its own rejection history in original organization after explicit switching. | PASS | Interactive results ledger |
| H03 | Member across organizations / FINANCE / ADMIN | Safe failure alerts go only to intended Finance/Admin | Finance UI displays actual UNKNOWN instructions; eight real SAP emails (four each Finance/Admin) have correct recipients, identities, employee, links and unique subjects, with safe reconciliation guidance. | PASS | H03-finance-notification.png |
| R01 | ADMIN / affected member | Successful edit confirmation survives revalidation | Repaired employee save displays confirmation after versioned redirect; persisted details retained. | PASS | FIX01-save-feedback.png |
| R02 | ADMIN / affected member | Historical profile cannot be deleted | Deleting employee with linked account and approved/exported history blocked; profile persists after reload. | PASS | FIX03-history-protected.png |
| R03 | ADMIN / affected member | Unused deletion respects cancel and persists confirmation | Unused deletion cancellation preserves profile; confirmed deletion removes it and list remains correct after reload. | PASS | Interactive results ledger |
| R04 | ADMIN / affected member | Member removal confirmation and access changes | Final member removal confirmation: cancel preserves active access, confirm revokes, explicit reactivation restores manager approvals. | PASS | Interactive results ledger |
| R05 | ADMIN / affected member | Final-admin protection survives final UI changes | Final removal UI still blocks the last active administrator after explicit confirmation. | PASS | Interactive results ledger |

### Independent durable scenario

`frontend/tests/browser/administration.spec.ts`: real registration/verification, organization creation, membership-only manager invitation, explicit manager account/profile link, employee project assignment, submission, approval and Finance Mock SAP export. Also final-admin protection, accepted-token replay by an unrelated account, mobile layout, project archiving, employee activation, protected deletion, cancelled deletion and unused-profile deletion. **1 passed in 2.7m, zero retries.** It ran against the first candidate; final membership wording/confirmation edits were subsequently exercised by R04/R05 and mobile checks on the final image.

## Findings and repairs

| Reference | Cause | Correction | Retest |
| --- | --- | --- | --- |
| DEF01 | Version-keyed employee forms remount after revalidation and lose successful `useActionState` feedback. Assignment itself persisted. | Redirect successful edit/link/status writes to refreshed detail with a version-bound, allowlisted confirmation. | R01 and durable regression: feedback visible, linked account persists and receives approvals. |
| DEF03 | No unused-profile deletion UI/API existed. | ADMIN/tenant/version-checked DELETE. Refuse linked accounts, direct reports, assignments including inactive ones, invitations including revoked ones, timesheets and manager decisions. Retain audit history. Confirmation and deactivation guidance. | R02/R03: protected history blocked; cancel preserves profile; confirm deletes and list remains correct after reload. |
| UX04 | Membership removal exposed a REVOKED label and generic confirmation only. | Explicit Remove from organization label, suspend/remove/reassign guidance, browser confirmation and retained success feedback. | R04/R05: cancel/confirm, revoke/reactivate and final-admin guard. |
| OBS02 | First link screenshot still showed unlinked UI. A second independent link updated automatically in 1,011 ms; both persisted. | No speculative backend linking change. Link success feedback covered by DEF01. | A04/A07/B04/B05. Not an established persistence or authorization defect. |

Managers require an explicitly linked employee profile and active MANAGER membership. No automatic name matching. Unlinked placeholders remain supported before invitation; they gain no review access until linked. History is preserved.

New API: `DELETE /api/v1/admin/employees/{id}`, JSON `{"version": <currentVersion>}`;204 success,409 stale/protected use,403 unauthorized role,404 unavailable/cross-organization resource. No migration or dependency changes; V1–V17 unchanged.

## Evidence and business results

Paths relative to ignored `.verification/phase12f/`:

- Onboarding/linking: `A01-admin-dashboard.png`, `A02-manager-member-no-profile.png`, `A04-manager-linked.png`.
- Defect/retest: `DEF01-save-feedback-missing.png`, `FIX01-save-feedback.png`, `FIX03-history-protected.png`. `DEF02-manager-link-not-persisted.png` is a historical diagnostic filename, not proof of lost persistence; see OBS02.
- Employee/manager: `B01-draft-totals.png`, `B03-pending.png`, `B04-manager-rejection.png`, `B05-approved-history.png`, `C01-reassigned-queue.png`, `FINAL-manager-pending.png`, `FINAL-employee-approved.png`.
- Finance: `B06-sap-success.png`, `B08-reports.png`, `F01-validation-failure.png`, `F02-retry-eligible.png`, `F03-unknown.png`, `F03-reconciled.png`, `FINAL-sap-success.png`.
- Tenant/notifications: `C06-beta-empty-finance.png`, `H01-notifications.png`, `H03-finance-notification.png`.
- Mobile: `D-admin-mobile.png`, `D-manager2-mobile.png`, `D-employee-mobile.png`, `D-finance-mobile.png`, `D-admin-projects-mobile.png`, `D-admin-employee-mobile.png`. Employee notification and Admin employee screenshots were visually inspected too.

October employee month: APPROVED 8.00 hours. The SAP Excel download was opened: two sheets, F12-E1, October 2026, stable identity/external reference, numeric 6.25+1.75=8.00 and no formulas. Actual report CSV/XLSX downloads were opened and checked for 8.00 hours. July filtered SAP-exported total is 1.25. Manager's independent October month has 1.00 pending hour; employee sees only its own 8.00 approved hours.

UI-created Mock SAP projects: `MOCK-INVALID` reached terminal FAILED without Retry; `MOCK-RETRY` reached four attempts/terminal failure, then Finance Retry succeeded on attempt 5; `MOCK-TIMEOUT` visibly reached UNKNOWN, then Finance Reconcile resolved the existing external record with one export attempt. July also has a historical UNKNOWN notification; it remains history after recovery, not current export state. No real SAP contacted.

Mailpit verified employee approval/rejection recipient, period and verbatim Greek/HTML-like rejection text. Eight additional SAP emails: four each FINANCE/ADMIN (August FAILED, July UNKNOWN/FAILED, June UNKNOWN), correct identity, employee, period, links, unique subject per recipient in this fixture and reconciliation guidance. Beta has no employee notification history. SENT correctly means email-server acceptance, not read status. These checks do not promise exactly-once SMTP delivery.

## Focused verification

Scoped Node: `.verification/runtime/node-v24.22.1-win-x64/node.exe`. Java 21 verification image: `maven:3.9.12-eclipse-temurin-21@sha256:c3c9d3ac4ce8431a3995c0318b8d390f448e693dd4fabc16e9b68d2e1f3d7b46`, separate workspace/cache and disposable Testcontainers PostgreSQL.

| Exact command/check | Result |
| --- | --- |
| `./mvnw -B -ntp test '-Dtest=PhaseElevenAdministrationTest#unusedProfileDeletionIsVersionedScopedAndAudited+deletionPreservesLinkedProfilesAndHistoricalDependencies' '-DargLine=-Xmx768m -XX:ActiveProcessorCount=2'` |2 passed,0 failures/errors/skips. XML and `backend-focused.log`. Class elapsed199.9s; Maven wall time includes an overnight host pause. |
| `./mvnw -B -ntp package -DskipTests` |PASS, `backend-package.log`. |
| Scoped Node `node_modules/eslint/bin/eslint.js src/app/app/admin tests/browser/administration.spec.ts`, frontend directory |PASS including final edits. |
| Scoped Node `node_modules/typescript/bin/tsc --noEmit`, frontend directory |PASS before final small membership/notice edits; final build separately completed TypeScript. |
| `docker build --progress=plain -f infra/production/frontend.Dockerfile -t workforce-frontend:phase12f .` |PASS, existing Dockerfile frozen pnpm install/build; compiled, TypeScript passed,25 pages generated. `frontend-build-final.log`. Two builds justified by final UI edits. |
| `docker build -t workforce-backend:phase12f .verification/phase12f` |PASS, verified packaged JAR/existing runtime. |
| Scoped Node `--test tests/smoke.test.mjs`, frontend directory with local app environment |28 passed,0 failed/cancelled/skipped, `smoke.log`. |
| Scoped Node `node_modules/@playwright/test/cli.js test tests/browser/administration.spec.ts --workers=1 --retries=0`, frontend directory |1 passed,2.7m, `browser-regression.log`. |
| Existing Compose selection plus Phase 12F image override, `config --quiet` |PASS. |
| Same selection, `up -d --no-deps --no-build --wait --wait-timeout 90 backend frontend`, then final `frontend` only |PASS, targeted replacement; volumes preserved. |
| Final Docker inspection |All six QA services healthy. |
| HTTP backend health, OpenAPI, Swagger, frontend login, Mailpit info |All200. |
| `git diff --check` |PASS; LF/CRLF informational warnings only. |
| `git check-ignore .verification/phase12f/state.json .verification/phase12f/sap-export.xlsx` |Both ignored. No credentials/artifacts staged. |

Browser configuration: `BROWSER_BASE_URL=http://localhost:33000`, `MAILPIT_API_URL=http://localhost:38025`, `PLAYWRIGHT_BROWSERS_PATH=<repo>/.verification/phase12e/resumed/browsers`, `PLAYWRIGHT_OUTPUT_DIR=<repo>/.verification/phase12f/regression`.

Compose selection: project`workforce-ci-493efe29fd43`, ignored env`.verification/phase12e-serial/stack.env`; files`compose.yaml`, `infra/ci/compose.yaml`, `.verification/phase12e/resumed/serial-local-images.yaml`, `.verification/phase12f/compose-images.yaml`. Secrets are not reproduced. Docker builds/access/Testcontainers and browser execution used approved outside-sandbox execution where required. No global toolchains changed.

No full backend/browser suite rerun: user requested targeted acceptance. Full candidate GitHub CI/backend/frontend/E2E should run before merge after a separately authorized push. Prior green CI is not evidence for this local checkpoint.

## Failed attempts and limits

Exploratory harness errors were corrected at the failing step and do not count as product passes themselves: nested Project/Status labels, Next's additional route-announcer alert, reading navigation before completion, expired-session reauthentication, organization picker after multi-membership logout, incorrect assumed invitation-token shape and same-document fragment navigation, older same-recipient invitation email, and evicted browser response body. Actual UI request inspection identified self-review resource without API writes. No arbitrary sleeps, automatic retries, permission changes or disabled assertions were added.

Generic Node REPL initialization failed; existing Playwright/Chromium worked through a scoped process. A workbook console print hit Windows Greek encoding; XML checks passed. One lint invocation had the wrong working directory and was corrected. PowerShell captured a NO_COLOR warning as NativeCommandError, but Playwright exited0 and reported1 passed. No application failure was hidden.

| ID | Additional case | Status | Limitation |
| --- | --- | --- | --- |
| N01 | Naturally expired invitation opened by its recipient | NOT TESTED | Invalid, cancelled-in-Admin and accepted-replay cases exercised. No database timestamps/clock altered; use a controlled short-expiry fixture later. |
| N02 | Event makes an undelivered reminder obsolete before worker claim | NOT TESTED | Local worker delivered normal events promptly. No SMTP/scheduler interruption manufactured. Existing cancellation CI is not counted as a 12F browser pass. |

No separate organization rename/settings screen, notification read/unread control or hard-delete assignment history exists. Organization selection, delivery history and assignment deactivation were tested; unsupported actions are not invented as passing scenarios. Small fixtures do not exercise multi-page pagination. Full accessibility, other browser engines/physical phones, load testing, production SMTP/SAP and process-kill recovery are outside this local acceptance.

A person without a manager may submit, but review waits for an eligible manager assignment. Suspension/removal preserves reporting links/history; Admin must reassign reports. The UI explains this; automatic reassignment is not promised. Interrupted SMTP acceptance can still duplicate physical delivery.

## Changed files and next action

Phase 12F-specific files included in the reviewed local checkpoint:

- `backend/src/main/java/com/acme/workforce/employee/api/EmployeeAdminController.java`
- `backend/src/main/java/com/acme/workforce/employee/api/EmployeeAdminDtos.java`
- `backend/src/main/java/com/acme/workforce/employee/application/EmployeeAdministration.java`
- `backend/src/test/java/com/acme/workforce/PhaseElevenAdministrationTest.java`
- `frontend/src/app/app/admin/actions.ts`
- `frontend/src/app/app/admin/admin-form.tsx`
- `frontend/src/app/app/admin/employees/[id]/page.tsx`
- `frontend/src/app/app/admin/employees/page.tsx`
- `frontend/src/app/app/admin/members/page.tsx`
- `frontend/tests/browser/administration.spec.ts`
- `docs/phase12f-browser-acceptance.md`
- `docs/local-browser-testing-guide.md`
- `docs/phase12-progress.md` (append-only summary)

No new migration, dependency, worker schedule or SAP contract. Preexisting Phase 13 operations/workflow/runbook edits remain separate and unstaged. The local checkpoint is titled `fix: complete Phase 12F browser acceptance and people management`; its hash is available from Git history rather than embedded in its own content.

Checkpoint review used the existing 56-scenario ledger, passing browser/smoke logs and two-test Surefire XML. It also rechecked the already-downloaded SAP workbook, whitespace and staged scope. No browser suite, build, container command or database operation was repeated to create the checkpoint. Private evidence remains ignored. Preservation hashes cover all 31 excluded Phase 13 files.

Covered customer acceptance is GO with N01/N02 and full candidate CI explicitly outstanding. Pushing requires separate authorization. Production security and cloud prerequisites retain their own gates. Phase 13 was not automatically resumed.
