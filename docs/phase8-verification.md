# Phase 8 verification report

Verification date: 2026-10-10. Branch: `feature/phase8-predeployment-qa`, based on Phase 7 commit `e378d7e`. The branch was already selected and the working tree was clean before implementation. Existing development data and Git history were preserved. No merge, push, deployment or Phase 9 work was performed.

## Release decision

Phase 8 is complete for the verified local scope and ready for staging preparation. No unresolved critical or high-severity defect was found in the exercised scenarios. This is not a production deployment sign-off: the explicitly untested scenarios and operational limitations below remain staging checks. Nothing was deployed.

## Changes and evidence

- Successful SAP exports can be downloaded as XLSX by FINANCE/ADMIN through the authenticated BFF.
- The two-sheet workbook uses the persisted job payload and successful export metadata, with no mutable employee/project lookup, SAP request, persistent generated file or object storage.
- V14 adds a database guard for SAP payload/identity immutability. V1–V13 are unchanged.
- Five initially failing regression tests demonstrated missing manager digest greetings, missing decision manager names, missing SAP stable identities, undifferentiated finance counts, and an unguarded SAP snapshot. All five passed after corrections.
- Existing timeout testing was made deterministic: a latch holds the response until the client has timed out; normal lookup requests have a reasonable timeout. The original assertions remain.
- New test setup defects (project reset and required export timestamps) were fixed. SMTP CRLF is normalized only in test comparisons; meaningful rejection text remains unchanged.

## Command results

| Command / suite | Result | Evidence |
|---|---|---|
| Java 21 `./mvnw test` | PASS — 299 tests, 0 failures/errors/skips | `backend/target/phase8-final.log`, Surefire XML |
| Final focused backend regression run | PASS — 40 tests, 0 failures/errors/skips | `backend/target/phase8-snapshot-final.log` |
| `./mvnw clean package -DskipTests` | PASS — final source packaged | `backend/target/phase8-package-final.log`; also final Compose Dockerfile build |
| `pnpm install --frozen-lockfile` | PASS | Node 24, pnpm 12.8.1; unchanged lockfile |
| `pnpm lint` | PASS, including final recheck | `.verification/phase8-frontend.log`, `.verification/phase8-frontend-final.log` |
| `pnpm typecheck` | PASS, including final recheck | Next route type generation + strict TypeScript; `.verification/phase8-frontend-final.log` |
| `pnpm build` | PASS | Production Node-runtime build |
| `pnpm test:smoke` | PASS — 18 tests | `frontend/tests/smoke.test.mjs` |
| `pnpm test:browser` | PASS — 10 workflows, 0 failures/skips | `.verification/phase8-browser-final.log` |
| Mock SAP `node --test` | PASS — 7 tests | `mock-sap/server.test.mjs`, `.verification/phase8-mock.log` |
| `docker compose config --quiet` | PASS | Existing six-service configuration |
| `docker compose up --build -d` | PASS — final source rebuilt and started | `.verification/phase8-compose-final.log`; volumes preserved |
| Six service health checks | PASS — PostgreSQL, Keycloak, backend, frontend, mock SAP, Mailpit healthy | Final `docker compose ps` inspection |
| Health / OpenAPI / Swagger | PASS — health UP, download route in OpenAPI, Swagger HTTP 200 | `/actuator/health`, `/v3/api-docs`, `/swagger-ui/index.html` |
| Isolated recovery script | PASS — SIGKILL/restart, mock restart, SMTP outage and PostgreSQL restart | `infra/dev/verify-phase8-recovery.ps1` |

The full backend run contained 299 tests. A subsequent historical-snapshot regression exposed one additional defect; after its fix, all 32 workbook tests and all 8 integrated business tests passed on final code. Thus 300 unique backend cases passed across the full run and final focused run, not a single 300-test full-suite execution. Class totals are Phase One 21, Two 29, Three 22, Four 28, Five 34, Six 49, Seven 43, SMTP 10, Phase Eight notification 24, workbook 32 and business 8. No tests were removed or skipped.

All Docker commands, Maven/Testcontainers runs, Node 24/pnpm commands, browser tests and local service verification required outside-sandbox execution. Source edits and Git inspection stayed in the workspace. No global Java/Node installation was changed. The container Maven wrapper enforced Java 21; the frontend used Node 24. No existing tracked CI configuration was present to modify.

## Notification matrix

Backend source-of-truth checks use PostgreSQL and an injected Clock. Mailpit transport checks compare actual recipient/subject/body, including UTF-8 and special characters. Delivery is plain text; HTML templates are not implemented, so HTML-body formatting is not applicable.

| Scenario | Expected | Observed / status | Test location |
|---|---|---|---|
| Employee reminder | Own name/email/month/link; only missing/DRAFT/REJECTED | PASS — PostgreSQL content and actual Mailpit delivery | PhaseEightNotificationTest.reminderExactConditionAndRecipient; PhaseSevenIntegrationTest; notifications.spec.ts |
| Active manager digest | Manager greeting, direct-report names/numbers/period/count only | PASS — PostgreSQL content and actual Mailpit delivery | PhaseEightNotificationTest.digestNamesManagerAndOnlyDirectReports / digestPendingCountAndNamesAreExact |
| Rejection | Owning employee, exact multiline Unicode comment, deciding manager, Athens decision time, reopen link | PASS — PostgreSQL content and actual Mailpit delivery | PhaseEightNotificationTest.decisionDisplayHandlesAthensDst; notifications.spec.ts |
| Approval | Owning employee, decision/manager/time, read-only instruction/link | PASS — PostgreSQL content and actual Mailpit delivery | PhaseEightNotificationTest.decisionIncludesAuthoritativeManagerAndAthensDecisionTime; notifications.spec.ts |
| SAP FAILED | Eligible finance/admin; identity, employee/period, terminal safe code/link | PASS — both FAILED/UNKNOWN PostgreSQL cases and actual Mailpit | PhaseEightNotificationTest.sapAlertIncludesStableIdentity; PhaseSevenIntegrationTest; notifications.spec.ts |
| SAP UNKNOWN | Same recipient scope; reconciliation required, no blind resend | PASS — PostgreSQL and final Mailpit browser workflow | PhaseEightNotificationTest.sapAlertIncludesStableIdentity; notifications.spec.ts |
| Monthly finance | Correct previous period, separate missing/pending counts, unresolved SAP counts/link | PASS — PostgreSQL counts and actual Mailpit delivery | PhaseEightNotificationTest.financeSeparatesMissingAndPendingCounts; PhaseSevenIntegrationTest |
| Duplicate evaluation/delivery claims | One intent per key; concurrent workers cannot share claim | PASS in existing PostgreSQL tests | PhaseSevenIntegrationTest.concurrentEvaluationsAreIdempotent / concurrentWorkersCannotClaimSameNotification |
| Calendar/DST | Jan, leap/non-leap Feb, 30/31-day months, midnight UTC, both Athens transitions | PASS — deterministic Clock; JVM default deliberately different | PhaseEightNotificationTest.calendarBoundariesIndependentOfJvm / decisionDisplayHandlesAthensDst |

Names resolve from authoritative application user records. Provisioning fallback is JWT name → preferred_username → generic Employee, never email-derived. Optional missing manager name is omitted. A historical notification intentionally preserves its enqueue-time content. Finance reminders retain the existing active-employee scope; manager preview remains bounded to 50 while count is complete.

## Business, security and concurrency matrix

| Scenario | Expected | Observed / status | Test location / limitation |
|---|---|---|---|
| Create/edit/delete multiple project entries | Correct daily/month totals, decimal safe | PASS — original suites and integrated business test | PhaseTwoIntegrationTest; timesheets.spec.ts; PhaseEightBusinessTest |
| Invalid date/unassigned/inactive project/daily limit | Structured rejection, no partial change | PASS in existing suite | PhaseTwoIntegrationTest |
| Submit/reject/reopen/resubmit/approve | Lifecycle and both immutable decisions preserved | PASS in existing backend/browser flow | PhaseThreeIntegrationTest; approvals.spec.ts |
| Pending/approved employee edits | 409; UI read-only | PASS | PhaseTwo/ThreeIntegrationTest; approvals.spec.ts |
| Full employee → manager → Finance → XLSX/report chain | One successful export; workbook and both report totals agree | PASS — integrated backend and actual browser chain | PhaseEightBusinessTest.completeBusinessLifecycleAndReportingAgreeWithExport; approvals.spec.ts |
| Unauthorized roles / own records / direct reports / no self review | 401/403/404 as documented, no state/history change | PASS — existing tests and all four new override cases | PhaseOne/Two/Three/Five/Six/SevenIntegrationTest; PhaseEightBusinessTest.noManagerOverride |
| SAP workbook roles | FINANCE/ADMIN allowed; EMPLOYEE/MANAGER 403; anonymous 401 | PASS — backend tests and anonymous BFF smoke | PhaseEightSapDownloadTest; sap.spec.ts |
| Workbook eligibility | Only APPROVED+SUCCEEDED+complete original snapshot | PASS — 32 workbook test cases | PhaseEightSapDownloadTest |
| Workbook historical accuracy | Current source edits do not change historical cells | PASS | PhaseEightSapDownloadTest.currentRecordsCannotRewriteExportHistory |
| Workbook safety | Literal formula-like strings, safe filename, numeric totals, native dates, bounded size | PASS | PhaseEightSapDownloadTest; sap.spec.ts; workbook.ts |
| Workbook transaction boundary | No transaction during POI, no SAP call | PASS | PhaseEightSapDownloadTest.formattingOutsideDatabaseTransaction |
| Duplicate month / concurrent submission | One winner, loser conflict; invariant intact | PASS — corrected isolated fixture; conflicts and invariants asserted | PhaseEightBusinessTest |
| Two tabs / concurrent entry changes / stale version | Newer draft retained; conflict surfaced | PASS existing backend and browser tests | PhaseTwoIntegrationTest; timesheets.spec.ts |
| Concurrent approve/reject | One decision and notification only | PASS — corrected isolated fixture; conflicts and invariants asserted | PhaseEightBusinessTest.concurrentApproveAndRejectHaveOneDecisionAndIntent; existing PhaseThree test passed |
| Concurrent export enqueue / worker claims | One export/job; no shared active claim | PASS | PhaseFiveIntegrationTest.concurrentEnqueueIsIdempotent / twoWorkersCannotClaimSameJob |
| Manual retry/reconcile racing completion | Completed success cannot be requeued | PASS — both reconciliation and retry races | PhaseFiveIntegrationTest.manualActionWaitingBehindCompletionCannotRequeueSuccess |
| BFF session/token boundary | HttpOnly encrypted session; no tokens in browser session; expiry redirects | PASS anonymous/session smoke; authenticated expiry reviewed, NOT TESTED dynamically | auth.ts, backend.ts; smoke.test.mjs |
| CSRF | Cross-origin/missing-origin logout rejected; mutations use Next server actions | PASS logout tests; server-action origin handling reviewed | smoke.test.mjs; Next.js server actions |
| Safe errors/logging/privacy | No raw SAP/SMTP body or secrets; own notification history | PASS automated safe-error/privacy tests; logging paths reviewed | PhaseFour/Five/SevenIntegrationTest; SmtpEmailGatewayTest; notifications.spec.ts |
| Desktop/mobile UI and logout | Navigation/actions accessible, no horizontal overflow | PASS — all 10 workflows | approvals, timesheets, SAP, reporting, notification browser specs |

## SAP and operational recovery matrix

| Scenario | Expected | Observed / status | Test location / limitation |
|---|---|---|---|
| SAP success/validation/authentication | Success reference; permanent failures terminal/safe | PASS | PhaseFour/FiveIntegrationTest; mock SAP tests; sap.spec.ts |
| Connection refusal / 500/503 | Bounded policy, stable identity; ambiguous HTTP outcomes reconcile before resend | PASS existing tests | PhaseFiveIntegrationTest; sap-worker.spec.ts |
| Timeout before persistence | UNKNOWN → lookup missing → eligible retry | PASS — synchronized response test, same assertions; initial timing flake fixed | PhaseFiveIntegrationTest.timeoutBeforeCreateReconcilesMissingBeforeRetry; mock SAP test |
| Timeout after persistence / duplicates | Lookup resolves same reference, no duplicate record | PASS existing backend/mock/browser flow | PhaseFiveIntegrationTest; sap-worker.spec.ts |
| Expired lease / stale token | Recovery through lookup; late result fenced | PASS persisted-claim simulation | PhaseFiveIntegrationTest.staleLeaseRecoversViaLookupAndFencesLateCompletion |
| Actual process SIGKILL/restart | Persisted claim survives, ABANDONED history, reconciliation succeeds, snapshot unchanged | PASS — real isolated process/service experiment | infra/dev/verify-phase8-recovery.ps1 |
| Mock SAP process restart | Accepted record survives | PASS — real isolated process/service experiment | Same script |
| Mailpit unavailable then restored | Durable intent retries and delivers | PASS — real isolated process/service experiment; stub transport tests passed | Same script; SmtpEmailGatewayTest |
| PostgreSQL restart | Backend reconnects; committed state preserved | PASS — real isolated process/service experiment | Same script |
| Notification stale PROCESSING | Lease recovery, old token fenced, attempt history retained | PASS simulation | PhaseSevenIntegrationTest.staleLeaseRecoversAndFencesOldWorker |
| Network calls outside DB transactions | Claim/reservation committed before call, result atomic | PASS | PhaseFour/Five/SevenIntegrationTest |

## Reporting

The complete PhaseSixIntegrationTest suite (49 tests) passed on the first full run. It covers NO_TIMESHEET, all lifecycle/SAP statuses, active employee counts, latest decisions, historical inactive projects, APPROVED versus SAP_EXPORTED hours, decimal totals, breakdowns, filters/pagination, CSV escaping/formula protection, Excel cell types and download roles. Existing browser reporting tests passed. The new integrated workflow adds a same-timesheet cross-check with the SAP snapshot workbook.

## Known limitations and staging checks

- SMTP cannot guarantee exactly-once physical delivery after ambiguous acceptance. Logical intent deduplication and claim fencing do not remove that limitation.
- Real production SAP, SMTP credentials, Render networking/deployment and load/penetration tests are outside this phase. Mock SAP is not proof of a production SAP contract.
- No real OAuth token-expiry wait/revocation experiment or automated accessibility audit has been performed; authentication boundary code was reviewed, normal OIDC login/logout and authorization are exercised.
- Lease recovery tests are explicitly distinguished from actual process termination above. Do not infer a process-kill PASS from a persisted-expired-claim test.
- Generated application workbooks remain in memory. Ignored test-only download artifacts are saved solely for verification.
- The XLSX snapshot bound (8 MiB and configured row cap) limits resource use; high-concurrency download load has not been measured.
- Legacy snapshots backfilled by V10 after successful export are explicitly rejected with HTTP 409 because original exported values cannot be proven. The application does not fabricate a historical workbook.
- Actual process termination was exercised during a SAP request, not during ambiguous SMTP acceptance. Notification expired-claim fencing was tested through persisted state; Mailpit outage/recovery was exercised with real service stops.
- Development fixtures use generated credentials stored only in ignored files. No real credentials, tokens, payload logs or secrets are committed.

Actual isolated recovery: the backend received SIGKILL after mock SAP persisted the document and before its delayed response completed. The committed PROCESSING claim remained. After restart/lease expiry, the worker wrote ABANDONED history, looked up the same identity and committed SUCCEEDED. The job retained exactly one export attempt and the same payload hash. A subsequent mock process restart retained the SAP record. Mailpit stop/start produced a recorded failed delivery followed by SENT; PostgreSQL stop/start preserved committed export/notification state and the backend reconnected. All temporary containers and their own temporary storage were removed; development volumes were untouched.

Mailpit full-content comparator: `infra/dev/verify-phase8-mailpit.ps1 -FixturePath <ignored QA fixture JSON>` exports only the four QA subjects' persisted intents and invokes `verify-phase8-mailpit.mjs`. It checks exact recipient list, subject and plain text (normalizing only SMTP CRLF and its final transport newline), no HTML body, and one matching delivery per intent. Final sample: 26 messages across all seven types passed (2 employee reminders, 4 manager digests, 2 rejections, 2 approvals, 6 FAILED, 8 UNKNOWN, 2 finance reminders). Browser tests independently verify authoritative Greek/accented names, comments, links and privacy. Business timestamp/period/count expectations are independently asserted in PostgreSQL tests, rather than inferred solely from the stored body.

UI review: desktop approved-timesheet and mobile manager-decision screenshots were visually inspected. Navigation/logout remained visible, Greek names rendered correctly, and controls fit the viewport. Existing UI event timestamps remain explicitly labelled UTC; notification decisions use Europe/Athens and workbook export timestamps are explicitly UTC.
A sixth concrete historical-data issue was found during migration review: V10 can backfill a payload after Phase 4 success. The new regression initially failed with HTTP 200 instead of 409 (`backend/target/phase8-legacy-before.log`). The reader now rejects a snapshot created after successful export with SAP_EXPORT_SNAPSHOT_INCOMPLETE. Final focused workbook/business verification passed all 40 tests; final packaging and Docker rebuild also passed.

## SAP workbook contract

`GET /api/v1/sap/timesheets/{timesheetId}/export.xlsx` returns the XLSX MIME type and a sanitized attachment filename such as `sap-export-EMP001-2026-10.xlsx`. The existing SAP detail page exposes **Download SAP Excel** only to FINANCE/ADMIN for SUCCEEDED exports; Spring independently authorizes and validates the request. The BFF preserves download headers and keeps bearer tokens server-side. Download failures are shown as safe UI errors.

- **Export Summary:** employee number, period, timesheet ID, export ID, stable identity, SAP reference, status, exported-at UTC timestamp, total hours and entry count.
- **Exported Time Entries:** work date, project code, numeric hours and literal description, followed by a numeric total row.

The workbook uses frozen headings, filters, bounded column widths and decimal/date formatting. Calculations use BigDecimal until Excel numeric-cell conversion; tests compare Excel numbers with tolerance. Dates before Excel's supported 1900 range use ISO text. User strings are explicitly string cells, never formulas. Snapshot and metadata are read consistently in a short read-only transaction; formatting happens afterward, without SAP calls. Generation is on demand in memory, so object storage and persistent generated files are unnecessary.

## Final Git state

Branch remains `feature/phase8-predeployment-qa`. Phase 8 changes are present as modified/new, uncommitted files. `git diff --check` passed. No merge into main/fixes, commit, push, history rewrite or deployment was performed. Existing development data was preserved. The normal local scheduler configuration was restored after accelerated browser verification.

## File inventory

New backend production files: `sap/api/SapExportDownloadController.java`, `sap/application/SapExportDownloadService.java`, `sap/domain/SapExportSnapshot.java`, `sap/infrastructure/SapExportSnapshotReader.java`, `sap/infrastructure/SapExportWorkbook.java`, and `db/migration/V14__protect_sap_export_payload.sql` (under the existing backend source/resource roots).

Changed notification files: `NotificationEvaluation.java`, `NotificationIntents.java`, `NotificationTemplates.java`. New backend tests: `PhaseEightSapDownloadTest`, `PhaseEightNotificationTest`, `PhaseEightBusinessTest`. Extended existing `PhaseFiveIntegrationTest`; no existing test was removed or skipped.

Frontend: new `src/app/api/sap/timesheets/[id]/export.xlsx/route.ts` and `src/app/app/sap/download-export.tsx`; updated `export-panel.tsx`; extended approval, SAP, notification browser specs and anonymous smoke tests; added test-only `tests/browser/workbook.ts` ZIP/XML inspection helper.

Operational verification scripts: `infra/dev/verify-phase8-recovery.ps1`, `infra/dev/verify-phase8-mailpit.ps1`, `infra/dev/verify-phase8-mailpit.mjs`. Updated README and architecture, notification, SAP integration/worker and reporting documentation. No production dependencies, Compose services, persistent document storage or AGENTS rules were added.
