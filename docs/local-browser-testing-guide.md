# Workforce — complete local browser testing guide

This guide describes the current application, including the Phase 12F corrections. It is a manual test procedure, not a claim that every listed variation has been automated. Recorded acceptance results and remaining limits are in [Phase 12F browser acceptance](phase12f-browser-acceptance.md).

## 1. Open the local application

| Open | URL | Purpose |
| --- | --- | --- |
| Workforce | http://localhost:33000 | Registration and all application workflows |
| Mailpit | http://localhost:38025 | Local verification, invitation and business emails |
| Workforce login | http://localhost:33000/login | Sign in or create an account |

Use synthetic email addresses ending in `@example.test`. Mailpit is a local inbox; you do not need a real mailbox. Do not send invitations to real customer addresses. SAP is a simulator here.

Use a new organization for your test, such as **GUI Acceptance 20261025**, and a unique organization address such as `gui-acceptance-20261025-01`. This avoids mixing new results with old fixtures. No database reset is necessary.

Use separate browser profiles for ADMIN, EMPLOYEE, MANAGER and FINANCE, or log out fully before changing accounts. Multiple incognito windows may share a session. Keep Mailpit in another tab. Open a recipient's email link in that recipient's browser profile, not in your signed-in Admin tab.

### Your four accounts

Choose a unique suffix for each run, for example `gui25a`:

| Account | Example local email | Organization role | Employee profile needed? |
| --- | --- | --- | --- |
| Organization creator | `gui25a-admin@example.test` | ADMIN automatically in the new organization | No |
| Employee | `gui25a-employee@example.test` | EMPLOYEE by invitation | Yes, to record time |
| Manager | `gui25a-manager@example.test` | MANAGER by invitation | Yes, linked to their account, to review reports |
| Finance | `gui25a-finance@example.test` | FINANCE by invitation | No, for Finance operations |

Create your own passwords during Keycloak registration. Do not use an account from the private automated-test ledger. Roles come from **organization membership**, not Keycloak role assignments. A login account, an organization membership and an employee profile are three different things.

## 2. Register ADMIN and create the organization

1. Open Workforce → **Create an account**.
2. Complete Keycloak's name, email and username fields, then **Register**.
3. Open Mailpit. Find **Verify email** addressed to your Admin email; check the recipient before opening it.
4. Open its verification link in the Admin browser profile. Complete the password fields and submit when requested.
5. At **Create your organization**, enter the organization name and unique address, then **Create organization**.
6. Confirm the dashboard shows your organization and **ADMIN**. Open **Administration**.
7. Reload the page. Confirm the organization and permissions remain.

Expected: a new empty organization, Admin navigation, and no unrelated employees/projects. The organization address is a unique slug, not a postal address. The current UI offers organization creation/selection; there is no separate organization rename/settings screen.

## 3. Create employee profiles before inviting people

Open **Administration → Employees**.

1. Under **Create employee**, enter employee number `GUI-E1`, employee name `GUI Employee`, and Active status.
2. Leave Manager and Existing member empty for now. Click **Create employee**.
3. Return to Employees. Create `GUI-M1`, named `GUI Manager`, Active, with no account or manager selected.
4. Confirm both profiles appear. Opening an employee name takes you to its detail/actions.

Expected: profiles initially say **No linked account**. Creating a profile does not create a login or grant organization access.

## 4. Invite EMPLOYEE, MANAGER and FINANCE

### Employee: invitation attached to a profile

1. Open `GUI-E1` → **Invite employee**.
2. Enter the employee email. Choose **EMPLOYEE**. Confirm that the optional Employee profile is `GUI-E1`.
3. Check the confirmation and click **Send invitation**.
4. Confirm the queued message and PENDING invitation in the list.

### Manager: deliberately test the previously confusing workflow

1. Open **Administration → Invitations** directly.
2. Enter the manager email. Choose **MANAGER**.
3. Leave **Employee profile (optional)** empty. This tests membership-only onboarding followed by explicit profile linking.
4. Check the confirmation and **Send invitation**.

### Finance: membership is sufficient

1. On Invitations, enter the Finance email and choose **FINANCE**.
2. Leave Employee profile empty; confirm and send.

Expected: each invitation has the correct role/email. Mailpit receives a separate **Workforce organization invitation** for each recipient. Delivery does not mean acceptance yet.

## 5. Accept each invitation through Keycloak

Repeat in the correct recipient's browser profile:

1. In Mailpit, locate the intended recipient's newest invitation for this organization. Check both recipient and organization in the message.
2. Open the complete acceptance link.
3. For a new account, click **Create an account**. Register with the **exact invited email**.
4. Open that recipient's verification email in Mailpit; verify and set a password when prompted.
5. Return to the organization invitation if necessary and click **Accept invitation**.
6. Confirm the app shows the right organization and role.

For an existing Keycloak account, use **Sign in with your work account** instead of creating another account, then **Accept invitation**.

As Admin, open **Members**, expand each person and confirm ACTIVE plus the intended role. Finance may correctly show **No employee profile** and will not appear in Employees unless you separately create/link one.

If Finance sees **Create your organization**, stop: that identity has no active membership. Do not create another organization to access the original company's reports. Check the invited email, acceptance, membership status and selected organization.

## 6. Link the new manager and assign the existing employee

This is the critical manager-onboarding test.

1. As Admin, open **Employees → GUI-M1**.
2. In **Connect an account**, enter the manager's email into **Search Existing member**, then click **Find Existing member**.
3. Select the correct active member. Check the account-link confirmation and click **Link member**.
4. Confirm **Account linked** appears and the profile now shows the manager's email. Reload and confirm it remains linked.
5. Open **Employees → GUI-E1**.
6. Under Employee details, search **Manager** for `GUI-M1`, click **Find Manager**, and select that profile.
7. Click **Save employee**. Confirm **Employee details saved**.
8. Reload. Confirm Manager is still `GUI-M1`.

Expected: the report relationship and the manager's authenticated account are connected. A profile with the same name is not sufficient. Links cannot be reassigned; verify the email carefully. A manager needs all three: ACTIVE MANAGER membership, an active linked employee profile, and direct reports pointing to that profile.

Unlinked manager placeholders are allowed while preparing invitations; they cannot receive authenticated approval access until linked. A self-assignment or reporting cycle must be rejected.

## 7. Create projects and assign them to the employee

1. Open **Administration → Projects**.
2. Create `GUI-A` / `Project Alpha` and `GUI-B` / `Project Beta`.
3. Open a project by clicking its code/name. **Save project** saves its details; **Archive project** separately disables future time entry. Do not archive a project to save it.
4. Open **Employees → GUI-E1 → Assign project**.
5. Search/find/select `GUI-A`. Set **Valid from** to `2026-01-01`, leave Valid to empty, Assignment status Active, then **Assign project**.
6. Repeat for `GUI-B`.
7. Expand both items in **Project assignments** and check project, active state and dates. Reload.
8. As Employee, open **My projects**, then the October 2026 timesheet later. Both projects should be available for valid October work dates.

Expected: project, assignment and employee must be active; the work date must lie inside assignment validity. A project merely existing in the organization does not make it available to everyone.

## 8. Employee draft: entries, edits, deletion and totals

As EMPLOYEE, open **Timesheets**, select/open **October 2026**. Direct route: http://localhost:33000/app/timesheets/2026/10 . Use a different unused month if needed, keeping assignment dates valid.

1. Click **Create draft**.
2. Add `2026-10-05`, `GUI-A`, `6.25` hours and a description → **Save entry**.
3. Add the same date, `GUI-B`, `1.75` hours → **Save entry**.
4. Confirm daily total and monthly total are **8.00**.
5. Edit Alpha to `5.50` → **Save changes**. Total should become **7.25**.
6. Add a separate `2.00`-hour row on another date. Total should become **9.25**.
7. Delete that new row. Total returns to **7.25**.
8. Reload. Confirm entries and 7.25 remain. Restore Alpha to 6.25 if you want an 8.00-hour final month.

Validation cases:

| Try | Expected |
| --- | --- |
| Missing project/date or zero/negative hours | Cannot save a valid entry; clear validation |
| Date outside the opened month | Date validation; no saved change |
| Add24 hours to a day already containing8 | Daily24-hour limit rejected; existing total unchanged |
| Month/date before all assignment start dates | Empty assigned-project choices and disabled Save entry |
| Unassigned/inactive project | Not available as a valid new-entry choice |

The current default daily maximum is 24 across projects, not 8. Work dates are calendar dates; event timestamps display Athens time.

## 9. Submit, reject, correct, resubmit and approve

1. Employee: **Submit month → Confirm submission**.
2. Confirm **PENDING_APPROVAL** and that entry editing/add/delete controls disappear.
3. Manager: open **Approvals**. The employee's October month should appear.
4. Open it. Check employee number, month, projects, descriptions and totals.
5. Reject with a meaningful comment, e.g. `Please correct Alpha hours. Έλεγχος & review <text>`; use the visible rejection/confirmation controls.
6. Employee: reopen the month. Confirm **REJECTED**, the exact comment and decision time. Check Notifications and the rejection email in Mailpit.
7. Click **Reopen draft**; edit the correction, save and submit again.
8. Manager: the month returns to Approvals. Open it → **Approve timesheet → Confirm approval**.
9. Employee: confirm **APPROVED**, no editing controls, and history retains both rejection and approval. Reload and sign in again to verify persistence.
10. Check the approval email is addressed to this employee, with the correct month.

Expected: managers only review active direct reports. No employee self-approval, manager self-review, unrelated-manager review or Admin/Finance approval override.

If Approvals is empty: confirm PENDING_APPROVAL, correct organization, employee.manager points to the intended profile, and that manager profile is active and linked to the ACTIVE MANAGER member. APPROVED or DRAFT months do not belong in the pending queue.

## 10. Finance: export approved hours to Mock SAP

1. As FINANCE, choose the correct organization and open **SAP exports**.
2. Open the approved employee/month. Confirm the **Simulated · Mock SAP** label.
3. Click **Export to SAP** once.
4. Observe QUEUED/PROCESSING then **SUCCEEDED**. Fast workers may complete before you see every intermediate state. Do not repeatedly click export.
5. Confirm an external SAP reference, export time and operation history.
6. Reload. SUCCEEDED/reference must persist, and Export to SAP must not be available again.
7. Click **Download SAP Excel**. Open the downloaded workbook.

Workbook checks:

| Sheet | Check |
| --- | --- |
| Export Summary | Employee number, period, timesheet/export IDs, stable identity, external reference, SUCCEEDED, export time, total8.00, number of entries |
| Exported Time Entries | Work dates, project codes, numeric 6.25 and1.75, descriptions, final total8.00 |

The file represents the immutable successful export snapshot. No export action should be available to EMPLOYEE or MANAGER. Those roles may see permitted read-only export state on their own/authorized approved timesheets.

## 11. Finance reports and downloads

1. Open **Reports**.
2. Set Year 2026, Month October. Click **Apply filters**.
3. Check employee row, monthly hours, approved count/hours and SAP-exported count/hours. If using this fresh fixture, approved/exported hours should both be 8.00 after successful export.
4. Filter Employee by `GUI-E1` and Project by `GUI-A`; apply. Check filtered tables.
5. Switch **Hours report mode** between Approved hours and SAP-exported hours. Apply and reload.
6. Click **Download CSV** and **Download Excel**. Open both and compare period, employee, project, dates and hours to the selected mode.
7. Use **Reset** to clear filters.

Operational summary cards describe whole-timesheet status/amounts for selected employees; the project filter selects matching employees. Hours report mode applies to project/detail data and downloads as explained on the page. Do not assume every summary card changes to a project-entry-only sum. A newly approved but unexported month contributes approved hours, not SAP-exported hours.

## 12. Local SAP failure, retry and reconciliation cases

Use separate unused months for these scenarios. As Admin create/assign the exact simulator project code; Employee records only that scenario's project, submits; Manager approves; Finance exports. Do not mix scenario projects into one month or rename a project after its export snapshot is reserved.

| Project code | Simulator behavior | What to check in Finance |
| --- | --- | --- |
| `MOCK-INVALID` | Validation rejection | Terminal FAILED, safe error, no normal Retry action |
| `MOCK-AUTH` | Authentication/authorization rejection | Safe terminal authentication error; no raw response/secrets |
| `MOCK-TRANSIENT-503` | First two requests fail503, then succeed | Scheduled attempts/backoff and eventual SUCCEEDED using same identity |
| `MOCK-TRANSIENT-500` | First two requests fail500, then succeed | Same bounded retry behavior |
| `MOCK-RETRY` | First four requests fail503, then succeed | With local four-attempt policy, terminal eligible FAILED → **Retry export** → success on fifth export attempt |
| `MOCK-TIMEOUT` | Creates remote record, response delayed | UNKNOWN → **Reconcile export** when eligible, or automatic reconciliation → SUCCEEDED without another creation |
| `MOCK-TIMEOUT-BEFORE` | First request delayed before creation | UNKNOWN must reconcile missing identity before safe resend; follow displayed job/history |
| `MOCK-SERVER` | Persistent503 | Bounded attempts, safe terminal failure; no infinite tight loop |

The Phase 12F browser run executed INVALID, RETRY and TIMEOUT; other rows are additional manual procedures, not claimed Phase 12F passes. Timing/attempt limits follow the running environment. If a job is already processing, an action can be temporarily unavailable. Use **Refresh export status** rather than repeatedly submitting. UNKNOWN means “check SAP first,” not “create another export.” Expand **Export technical details** and **Recent operation history** to compare identity and attempts.

## 13. Change managers and verify approval access

1. Invite/register/link a second manager `GUI-M2` using the same UI steps.
2. Submit a new employee month and leave it PENDING_APPROVAL under Manager1.
3. Admin: change GUI-E1's Manager to GUI-M2 → **Save employee**, confirm and reload.
4. Manager1: refresh Approvals; the pending month must disappear. A previously saved review URL must deny access.
5. Manager2: Approvals should contain it and permit review.
6. Admin: select Manager **None**, save/reload. Neither manager should review it.
7. Reassign GUI-M2; its queue should return.

Additional checks: assigning oneself as manager or creating M1→M2→M1 must fail. An inactive manager cannot be saved as the employee's manager. An unlinked placeholder grants no approval access. Do not confuse retaining a profile with retaining authenticated permissions.

A profile without a manager may currently submit; its pending month needs an Admin manager assignment before anyone can review it. No automatic reassignment occurs.

## 14. People lifecycle and safe removal

Use a disposable user/profile first, or restore the test role/status afterward. Keep the only Admin active.

| Action | Exact place | Expected result |
| --- | --- | --- |
| Edit profile | Employees → person → Employee details → Save employee | Confirmation; new fields persist after reload |
| Link account | Unlinked employee → Connect an account | Select correct existing member, confirm, Link member; linked account cannot be reassigned |
| Change role | Members → expand person → Organization role → confirmation → Update membership | New role on subsequent requests; refresh/re-login updates navigation |
| Deactivate employee | Employees → person → Employment lifecycle → checkbox → Deactivate employee | History retained; new employee work blocked; membership managed separately |
| Reactivate employee | Same place → checkbox → Activate employee | Eligible employee operations restored |
| Suspend member | Members → expand → Membership status Suspended → checkbox → Update membership | Organization access blocked, history preserved |
| Reactivate member | Same place → Active → checkbox → Update membership | Access restored according to role/profile |
| Remove member | Same place → Remove from organization → checkbox → Update membership → confirm dialog | Membership revoked; no history/login account deletion |
| Cancel invitation | Invitations → pending recipient → checkbox → Revoke invitation | REVOKED; cannot grant new access |
| Delete unused profile | Unlinked, unused employee → Delete unused profile → confirm | Permanently removed from directory; audit retained |

For deletion, first **Cancel** the browser dialog and reload: profile must remain. Then repeat and confirm: unused profile should disappear with confirmation. Try a historical/linked employee: deletion must be blocked with “Deactivate it instead.” Even inactive assignments or revoked invitation history block permanent deletion. This is deliberate historical protection.

Try changing/removing the last active Admin: the server must reject it. Restore any test role changes. Before suspending/removing a manager, reassign direct reports; links/history are preserved and the suspended/revoked manager cannot approve.

## 15. Invitations and account-link edge cases

| Case | Procedure | Expected |
| --- | --- | --- |
| Duplicate pending invite | Send the same email/role again before acceptance | Clear conflict; no duplicate active invitation |
| Unused invitation | Send to a disposable email, then Revoke invitation | List shows REVOKED |
| Accepted link reopened by intended member | Reopen original email after acceptance | No duplicate membership or elevated privileges |
| Accepted link opened by another account | Open in a different authenticated profile | No acceptance/access for wrong identity |
| Wrong email | Sign in with an account different from invited email | Cannot accept that invitation |
| Already-linked member | Try choosing that account for a second employee profile | Excluded/blocked, never silently reassigned |
| Expired invitation | Use a naturally expired fixture | Must not grant access; this variation was NOT TESTED in Phase 12F |

If an invitation is already pending for a profile, revoke it before attempting a different explicit account link. Do not match accounts by display name.

## 16. Multiple organizations and role isolation

1. Admin: click **Organization → Create another organization**; create a second empty organization with a different slug.
2. Invite the existing employee email to the second organization as **FINANCE**, without an employee profile.
3. Employee profile/browser: open the second organization's email, sign in with the existing account and **Accept invitation**.
4. Click **Organization**, select organization2 and **Continue**.
5. Confirm FINANCE navigation, empty second-organization SAP/report data and no first-organization notification history.
6. Try a saved first-organization SAP/detail URL while organization2 is active: it must not expose the record/download.
7. Switch back to organization1: role EMPLOYEE and own timesheet history should return.
8. As Admin, switch organizations and compare Employees/Projects/Members. First-organization profiles must not appear in second-organization manager/assignment lookups.

Reload other open application tabs after switching. Roles are per organization. Revoking membership in organization1 must remove access there while independently authorized organization2 remains available.

## 17. Authorization checks from the address bar

Log in under each role and open these actual pages. Denial can be a safe403-style message or an unavailable/404 page; it must never expose data or actions.

| Route | EMPLOYEE | MANAGER | FINANCE | ADMIN |
| --- | --- | --- | --- | --- |
| `/app/admin/members` | Denied | Denied | Denied | Allowed in active organization |
| `/app/approvals` | Denied | Direct reports only | Denied | No manager override |
| `/app/sap` | No export commands | No export commands | Allowed | Allowed |
| `/app/reports/monthly` | Denied | Denied | Allowed | Allowed |
| `/app/notifications` | Own history | Own history | Own history | Own history |

Additional checks: manager self-review denied; unrelated manager review URL denied; approved employee month read-only; revoked member cannot reopen protected organization pages. Logout then try `/app`: sign-in must be required. Hiding navigation alone is not proof; direct-page denial is part of this procedure.

## 18. Notifications and Mailpit

1. Generate rejection, approval and SAP failure/UNKNOWN through the above UI workflows.
2. Employee → **Notifications** → expand **Message**. Check exact employee/month, actual rejection comment and action link.
3. Finance/Admin → Notifications: inspect relevant SAP alerts. UNKNOWN must instruct reconciliation, not blind resend.
4. In Mailpit, search the exact synthetic recipient. Check recipient, subject, month, names, comment text and link organization/context.
5. Test Greek names, ampersands and HTML-like comment text. It must display as text, not executable HTML.
6. Reload Notifications; no cross-organization history should appear.

History is not an inbox with read/unread controls. SENT means SMTP server accepted the email. A historical FAILED/UNKNOWN alert can remain after the export later succeeds; open the export to inspect current state. CANCELLED means the reminder became obsolete before delivery. Ordinary repeated evaluation should not create duplicate business-event messages, but SMTP interruption can duplicate physical delivery. Controlled obsolete-reminder cancellation was not browser-tested in Phase 12F.

## 19. Mobile, keyboard and logout

Use a narrow browser window or responsive mode around 375×812. Repeat for all four roles:

1. Open Overview and every role-specific navigation item.
2. Check headings, labels, buttons, forms and page width. Tables may scroll inside their own container; important actions must remain reachable.
3. Admin: Employees, employee details, Members, Invitations, Projects. Expand a member and an assignment.
4. Manager: Approvals and a review. Employee: Timesheets and a month. Finance: SAP detail and Reports.
5. Use Tab to reach fields/buttons; press Enter in an Admin search field to submit its filter.
6. Click **Log out**; complete Keycloak's **Logout** confirmation when shown.
7. Open `/app`: it must require login. Sign in again and verify the saved organization/data. Repeat logout on desktop.

With multiple memberships, fresh login can show **Choose your organization**. Select the intended one; this is expected, not a failed login.

## 20. Common problems and what to check

| Symptom | Check |
| --- | --- |
| Finance absent from Employees | Finance needs membership, not an employee profile. Check Members and active organization. |
| Finance lands on Create your organization | Exact invited email, accepted invitation, ACTIVE membership. Do not create a second organization as a workaround. |
| Manager has no pending approvals | Account/profile link, active MANAGER membership/profile, employee's saved Manager, correct organization and PENDING_APPROVAL state. |
| Choose assigned project is empty | Employee/project/assignment active; assignment dates cover selected work date; correct organization; project actually assigned to this employee. |
| Save appeared to do nothing | Look for confirmation/error; reload saved record. Current repair preserves employee success feedback. Do not click Archive to save project details. |
| Cannot delete employee | Linked account or historical dependencies exist. Deactivate employee and separately suspend/revoke membership. |
| Role navigation stale | Reload/new login; verify selected organization. Server still checks every authorized request. |
| SAP stuck or uncertain | Read status/history, use Refresh export status; UNKNOWN requires reconciliation. Do not create another logical export. |
| No invitation email | Check exact Mailpit recipient and invitation delivery state; use the newest email for the correct organization. |
| Another account appears after opening link | Browser profiles share cookies/session. Fully log out or use an independent profile for the recipient. |

## 21. Record your manual result

For each section, note account/organization, action, expected/actual outcome and PASS/FAIL/BLOCKED/NOT TESTED. For failures, capture the screen and visible message, route without secret invitation token, month and employee/project code. Never share passwords, JWTs or full acceptance links.

Minimum acceptance sequence: sections 2–11, manager reassignment/removal from13–14, tenant/authorization checks 16–17, notifications 18 and all-role mobile/logout 19. Add simulator/negative cases 12/15 as needed. Remaining Phase 12F limits are explicitly recorded in the acceptance report; this manual does not turn an unexecuted variation into a pass.
