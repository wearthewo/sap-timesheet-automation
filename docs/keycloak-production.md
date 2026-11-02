# Keycloak behind the production proxy
The optimized Keycloak 26.8.1 image starts with `start --optimized --import-realm`, PostgreSQL persistence and management health enabled. It does not use start-dev. Production realm SSL, confidential code flow, PKCE S256, state, verified email, password policy and brute-force protection are retained. Password grants/service accounts are disabled for the Workforce client. Client scope is restricted; application roles remain PostgreSQL organization memberships.

Public hostname is the fixed `https://AUTH_HOST`; browser callbacks are exactly `https://APP_HOST/api/auth/callback/keycloak` and `.../keycloak-register`. Logout returns to the fixed application login. No wildcard redirect or origin. Review the generated realm and actual imported client, not just environment variables.

Caddy terminates TLS and sets canonical forwarding headers after removing supplied forwarding/original/trace headers. Keycloak trusts only the configured proxy IP. No public route to /admin, master realm or port 9000. Internal management should use an operator's SSH-controlled private path or Docker exec/client, with short-lived admin tokens that are never printed. Do not temporarily open the admin console to the Internet. A container on the private network is privileged infrastructure; do not grant untrusted workloads access.

Read [Keycloak reverse proxy](https://www.keycloak.org/server/reverseproxy), [hostname configuration](https://www.keycloak.org/server/hostname) and [container guidance](https://www.keycloak.org/server/containers). Caddy security/limits are in the deployment file; a local CA pass is not proof of public ACME renewal.

Fresh realms import once. Existing realms are not overwritten by --import-realm. A future upgrade requires backup, version compatibility review and explicit controlled client/SMTP/hostname changes in a disposable restored copy first. Never apply this import to the original V14 development realm as a shortcut.

If login fails: inspect safe HTTP status and issuer/callback host, service readiness, exact client ID/secret coordination and private token/JWK connectivity. Confirm clock synchronization. Do not paste tokens, email action links or query strings into logs/tickets. Check that issuer is public HTTPS while any private endpoint override is intentionally configured and trusted. Verify logout clears the BFF and organization cookies and terminates Keycloak SSO.

Bootstrap admin credentials are initial recovery material, not employee logins. Establish named operator accounts and recovery procedures before removing bootstrap material. Public registration needs working verified SMTP and abuse controls; synthetic preverified fixtures are not a substitute for that launch gate.
