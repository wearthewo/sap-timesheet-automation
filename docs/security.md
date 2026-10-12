# Application authorization and tenant isolation

Keycloak authenticates identity; PostgreSQL authorizes business access. JWT signatures, issuer, audience and expiry are validated by Spring Security. The organization selector is not an authority: X-Organization-Id must identify an active organization with an active membership for the authenticated subject. Membership role changes take effect on subsequent requests without minting a new JWT.

| Capability within selected organization | EMPLOYEE | MANAGER | FINANCE | ADMIN |
|---|---|---|---|---|
| Own employee timesheets (profile required) | Yes | Yes | Yes | Yes |
| Project catalog | Yes | Yes | Yes | Yes |
| Create/update projects and assignments | No | No | No | Yes |
| Direct-report approval, active manager profile, no self-review | No | Yes | No | No |
| SAP enqueue/retry/reconcile and worklist | No | No | Yes | Yes |
| Finance reports and protected downloads | No | No | Yes | Yes |
| SAP export state | Own | Own/direct reports | Organization | Organization |
| Notification history | Own | Own | Own | Own |

No role bypasses organization membership. Root lookups filter by organization; child reads follow an authorized root. Composite foreign keys prevent cross-organization project assignments, managers, entries, decisions, exports/jobs/attempts and notification recipients. Ownership is immutable. Organization-wide reads never use the deprecated global app_users.role. Public health and API documentation contain no business data.

The Next.js BFF retains tokens in its encrypted HttpOnly session and sends them only server-to-server. Organization selection uses a verified Server Action and an HttpOnly SameSite cookie. Server Actions preserve Next.js origin protections; logout is POST-only with explicit origin validation. Backend bearer APIs remain stateless. Backend authorization protects file downloads independently of navigation visibility.

Workers receive persisted organization IDs, not browser context. SAP and email calls happen outside DB transactions. Active-organization claiming, lease recovery, claim tokens, immutable payloads and immutable attempt history remain enforced. Notification recipient membership is rechecked when claiming. Revocation cannot recall an operation already in flight. Raw credentials, tokens, provider bodies and sensitive payloads must not be logged.

Use trusted SQL only for membership administration in this phase. Never infer organization access from email domains, Keycloak role claims or an arbitrary frontend role field. Legacy-data migration requires an explicit reviewed ownership confirmation; see [Phase 9 verification](phase9-multitenancy-verification.md). Real SAP routing/credentials per organization, audited membership administration and onboarding are later work.
