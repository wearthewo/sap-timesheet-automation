# Production environment reference
Copy `infra/production/production.env.example` outside the checkout. Production and synthetic QA validation are separate: `--local-test` allows .test names and local CA only for disposable verification.

| Setting | Purpose |
|---|---|
| APP_HOST / AUTH_HOST | Distinct DNS hostnames, no scheme/path; public origins always HTTPS |
| TLS_CONFIG | ACME contact email; `internal` only in isolated tests |
| PLATFORM | linux/amd64 or linux/arm64 |
| RELEASE_TAG | Unique immutable release identifier; do not reuse a deployed tag |
| PUBLIC_BIND / HTTP_PORT / HTTPS_PORT | Host ingress binds; production defaults 0.0.0.0, 80, 443 |
| INGRESS_SUBNET / PROXY_IP | Non-overlapping Docker subnet and fixed trusted proxy address |
| SECRET_DIR | Absolute root-controlled directory of named secret files |
| SMTP_HOST / SMTP_PORT / SMTP_USERNAME | Authenticated submission provider; prepared for port 587 STARTTLS |
| MAIL_FROM | Provider-verified sender address |
| KEYCLOAK_ADMIN_USERNAME | Private bootstrap operator; remove bootstrap credentials after establishing recovery/admin policy |
| BACKUP_DIR | Absolute restricted encrypted archive directory outside checkout |
| AGE_RECIPIENTS_FILE | Public age recipients; safe to supply to backup job |
| AGE_IDENTITY_FILE | Offline private key temporarily supplied **only** during restore |
| BACKUP_RETENTION_DAYS | Local encrypted retention; default 14 |

Secret filenames: PG_ADMIN_PASSWORD, DATABASE_PASSWORD, KEYCLOAK_DB_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_CLIENT_SECRET, NEXTAUTH_SECRET, SAP_API_TOKEN, INVITATION_SIGNING_KEY, SMTP_PASSWORD. Never pass their contents as Docker build arguments or command-line arguments. Compose outputs contain paths/names, not their contents.

The separate databases are `workforce` and `keycloak`. JDBC URLs, JWT issuer/audience, BFF endpoints and worker limits are explicit in Compose. PostgreSQL login roles have database-level PUBLIC access revoked. Schema ownership remains with the corresponding application login for Flyway/Keycloak migrations.

Workers: SAP batch 3, poll 5s, four attempts, initial backoff 30s capped 10m, lease 5m, reconciliation 30s. Notifications batch 5, poll 5s, evaluation 15m. Existing leases, fencing and SMTP ambiguity semantics remain unchanged. Both business calendars remain Europe/Athens; durable timestamps UTC. Backend readiness checks DB; liveness remains process health. No dependency on external SMTP/Mock SAP for permanent application startup failure.

Secrets rotate by coordinated operator action: update database password and corresponding file together; update OIDC client secret and frontend together; rotate NextAuth secret to invalidate sessions; coordinate Mock SAP token in both services. Invitation HMAC rotation invalidates outstanding links unless a separately designed key transition exists; revoke/reissue deliberately. Preserve configuration and required keys in an independent secure recovery store.

SMTP uses authentication, STARTTLS required, and hostname checking for Spring; Keycloak 26.8.1 requires STARTTLS when enabled and uses default hostname verification. No real SMTP credential or delivery was used in 12D. DNS/provider verification, SPF, DKIM, DMARC alignment, verified sender and recipient tests must precede public registration. Provider failure must leave durable notification intents eligible for existing bounded retry; never log provider bodies/credentials. Ambiguous SMTP acceptance can duplicate physical delivery. Keycloak's own verification/reset email is not the Workforce outbox.
