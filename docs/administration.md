# Organization administration

ADMIN members manage only the organization selected in the BFF. Spring resolves the effective role from an active database membership on every request; `X-Organization-Id` is a selector, never authorization. EMPLOYEE, MANAGER and FINANCE cannot invoke these administration mutations. ADMIN remains unable to override the direct-manager approval policy.

## Set up an empty workspace

1. Register and verify your email through Keycloak, then create an organization.
2. Open **Administration → Employees**. Create employee numbers and names; account links and managers can initially be empty.
3. Invite a manager from their employee detail, choosing MANAGER. Invite employees using EMPLOYEE and their unlinked profiles. FINANCE and ADMIN memberships may exist without employee profiles.
4. Recipients open the email link, sign in or register with that exact verified email, and accept. No Keycloak administration console is required.
5. Assign each employee an active manager. Linked managers require an active MANAGER membership; unlinked placeholders may be prepared before their invitation is accepted.
6. Create projects, then assign them from employee or project detail, including validity dates. Eligible employees can immediately use these in the existing timesheet flow.

Membership and employment are separate lifecycles. Suspend/revoke membership to remove organization access; deactivate employment to stop new employee work without deleting history. Reactivation is explicit. Changing a manager's membership role or employment state does not silently reassign reports; review those relationships as part of the change. Existing approval checks require an active direct manager with MANAGER membership.

## APIs and integrity

All administration routes require an active ADMIN membership:

| Route under `/api/v1` | Methods / purpose |
| --- | --- |
| `/admin/employees` | GET paginated search/filter/sort; POST create |
| `/admin/employees/{id}` | GET profile; PUT profile/manager update |
| `/admin/employees/{id}/status` | POST explicit active/inactive change |
| `/admin/employees/{id}/link` | POST link an existing active member |
| `/admin/members` | GET paginated search/status filter |
| `/admin/members/{id}` | PUT role/status |
| `/admin/invitations` | GET paginated status filter; POST invite |
| `/admin/invitations/{id}/revoke` | POST revoke pending invitation |
| `/admin/projects` | GET paginated search/filter/sort |
| `/admin/projects/{id}/status` | POST activate/archive |
| `/admin/employees/{id}/assignments` | GET paginated assignments |
| `/admin/projects/{id}/assignments` | GET paginated assigned employees |
| `/projects`, `/projects/{id}` | Existing POST/PUT project writes, now audited |
| `/project-assignments`, `/project-assignments/{id}` | POST create; PUT validity/active update |

Employee and membership mutations carry `version` in JSON and return HTTP 409 for stale versions. Project/assignment writes retain the existing API convention. Organization-row locking serializes administration changes; membership permission is rechecked after acquiring the lock. Last-active-ADMIN protection covers demotion, suspension and revocation, including competing API requests, and has a database update guard. No hard-delete API is exposed. Employee-account linkage, once established, cannot be reassigned. Database composite foreign keys preserve tenant ownership. Manager cycles are rejected by a recursive database guard under the organization lock.

V17 adds nullable employee account linkage, optional profile names (legacy records fall back to identity names), version columns, invitation persistence and append-only administration audit. Legacy IDs and history are not rewritten. Employee numbers and project codes retain organization-scoped uniqueness. Assignment policy remains one active assignment per employee/project pair, with inclusive validity bounds; inactive historical assignments may coexist. Archiving projects keeps historical entries and immutable SAP snapshots intact.

## Invitation security and delivery

Set an independent random `INVITATION_SIGNING_KEY` of at least 32 characters, e.g. generate 32 random bytes as hex. `INVITATION_VALIDITY` defaults to `PT168H`, bounded to 30 days. Missing signing configuration disables invitation creation without preventing the rest of the application from starting. This key is server-only; never use the SAP token or OIDC secret for it.

The bearer token combines a random invitation UUID and an HMAC-SHA256 authentication value. Only its SHA-256 hash is stored. A durable notification stores the invitation ID and a safe template, allowing the existing worker to derive the acceptance link transiently. Plaintext tokens are not persisted in the outbox or audit log. SMTP runs outside database transactions and uses the existing leased, fenced worker and safe error handling. Rotating the signing key prevents delivery of unsent invitations created with the old key; revoke and recreate those invitations. Already delivered links remain subject to their stored hash, expiry and status.

The email uses `/invitations/accept#token=…`. The browser removes the fragment before posting to a same-origin context endpoint, which sets a short-lived HttpOnly cookie. OIDC callbacks use the fixed `/invitations/review` path; the token is never placed in the callback URL or rendered into a form. Logout clears invitation context. Backend `/invitations/preview` and `/invitations/accept` POSTs require authenticated, boolean-verified-email JWTs, but no existing organization membership. The backend checks the invited email against the trusted JWT claim, never a submitted email field.

Acceptance locks the organization and atomically creates membership, links the optional profile, consumes the invitation and appends an audit record. Existing memberships are not reactivated or elevated through invitations. Repeating acceptance by the original accepting identity with an active membership is idempotent; another identity cannot consume it. Revoked/expired invitations cannot grant access. Pending uniqueness covers organization/email and unlinked employee. Expiry is checked at use and rendered as EXPIRED; a subsequent invite materializes expired pending rows before reserving the next invitation.

Invitation status and delivery state are visible to administrators; bearer tokens are never returned by administration APIs. A revoke racing with an SMTP send can still produce a physical email, but the link cannot be accepted after revocation. Ambiguous SMTP acceptance may cause duplicate physical delivery; invitation consumption is nevertheless database-atomic. An inactive/suspended organization cannot accept invitations. New identities receive only the invitation's authorized organization role, never a realm role.

Invitations remain organization-issued grants until accepted, expired or explicitly revoked; changing the issuing administrator's membership does not automatically revoke them. Review outstanding invitations when changing administrative access.

## Operational limits

Searchable form selectors return at most 100 active matches; refine search for large organizations. Lists and assignment histories paginate. Administration changes have immutable safe audit rows; an audit browsing UI, invitation resend action, bulk imports, onboarding automation and production email delivery are outside this phase. No files or object storage are added.

Do not run the new stack over the original V14 development database without the documented controlled legacy mapping/migration and Keycloak realm update. Verification uses independent Compose projects and PostgreSQL volumes.
