# Production deployment architecture
The prepared target is an AMD64 Ubuntu 24.04 host running the production Compose topology. Public registration uses HTTPS without a visitor allowlist. Follow [release prerequisites](public-production-readiness.md) and the [operator runbook](deployment-runbook.md). Local image IDs are not published registry digests.
This is a reviewed deployment **template**, not a deployed service or Phase 13 approval. Existing local/CI Compose files, databases and realms are independent and unchanged. See [checkpoint evidence](phase12-progress.md), [environment reference](production-environment.md), [Keycloak](keycloak-production.md), [backups](backup-restore.md), [operations](operations.md) and [launch checklist](public-production-readiness.md).

## Topology
```text
Browser -- HTTPS --> Caddy -- private HTTP --> Next.js Node BFF
                         \-- private HTTP --> production Keycloak
Next.js -- private HTTP --> Spring Boot API + durable workers
Spring Boot + Keycloak --> PostgreSQL (separate databases and logins)
Spring Boot --> private HTTP Mock SAP (persistent volume)
Keycloak + Spring Boot --> authenticated STARTTLS SMTP (external, not configured here)
```
Only Caddy publishes TCP 80/443. PostgreSQL, Mock SAP, Spring diagnostics and Keycloak administration/management have no public ports. Caddy permits only the workforce realm and static Keycloak resources. The backend remains one modular-monolith artifact with PostgreSQL jobs; no new worker deployment or queue.

Production definitions are under `infra/production/`. Never merge them with root development/CI Compose. Use project `workforce-prod` with a new volume set. No Mailpit in this stack. Mock SAP is explicitly a simulator, not a production SAP adapter.

## Prepared operator commands (not permission to deploy)
On a Linux host with Docker Engine/Compose plugin, Node 24 for preflight, bash, flock, SSH and restricted operator access:
1. Review the exact Git revision, dependencies and target architecture; build off the serving host where possible.
2. Copy `production.env.example` to `/etc/workforce/production.env`; supply real hostnames, SMTP settings and unique release tag. Never source an untrusted environment file.
3. Create `/etc/workforce/secrets` mode 0700. Supply each named file using a password manager, at least 32 random characters. Files can be 0444 **inside the 0700 parent** to allow explicitly mounted nonroot containers to read them; Compose bind-mounted file secrets do not reliably honor uid/mode overrides. Validate actual host permissions.
4. Configure public backup recipient(s); keep private age keys offline. Never put private keys in routine backup service mounts.
5. Run:
```sh
node infra/production/validate-env.mjs --env-file /etc/workforce/production.env
docker compose -p workforce-prod --env-file /etc/workforce/production.env -f infra/production/compose.yaml config --quiet
```
These are configuration checks only. Publication and deployment must use the signed release procedure in the [operator runbook](deployment-runbook.md), not an ad hoc build/start command. Review before any real execution. Do not print interpolated configuration, inspect process environments or enable shell tracing in diagnostics. The file-secret loader fails on missing/empty/placeholder secrets. Docker administrators/root can still read mounted files and process environments: this is runtime injection, not a secret vault.

## Resource limits
| Container | Memory ceiling | CPU ceiling |
|---|---:|---:|
| Caddy | 256 MiB | 0.5 |
| PostgreSQL | 1,536 MiB | 1 |
| Keycloak | 1,280 MiB (512 MiB heap) | 1.5 |
| Spring + workers | 1,536 MiB (768 MiB heap) | 1.5 |
| Next.js | 768 MiB (512 MiB JS heap) | 1 |
| Mock SAP | 256 MiB | 0.5 |

Combined ceilings are 5.5 GiB, leaving roughly 2.5 GiB on an 8 GiB host for OS/cache/headroom; a 12 GiB host has more headroom. CPU ceilings are caps, not reservations; their sum exceeds a small host's cores. Backup runs while writers are quiesced, with 512 MiB memory and 512 MiB plaintext tmpfs. Large backups require measured sizing. Builds/test runners require a separate resource budget. These are initial limits, not throughput or user-capacity guarantees. See measured 12C/12D evidence.


## Architectures and image updates
+
+The current release workflow builds **linux/amd64**. Some upstream image indices support multiple architectures, but that does not establish full-stack ARM64 compatibility; the custom Keycloak build has its own architecture constraints. See [Keycloak adoption](phase13-keycloak-adoption.md).
+
+`images.lock.json`, the production Dockerfiles and the custom Keycloak input manifest record immutable inputs. Updating them is an explicit maintenance operation requiring dependency integrity checks, focused compatibility tests and fresh scans of final images. Retain the exact source revision, manifests, SBOMs and provenance; package repository resolution is not a claim of bit-for-bit reproducibility. A base-image scan does not clear the assembled application image.
+
+## Fresh versus existing data
Fresh PostgreSQL initializes separate `workforce` and `keycloak` database owners; neither is superuser. Flyway V1–V17 runs only on the selected new workforce database. `LEGACY_TENANT_CONFIRMED=false` deliberately blocks an unreviewed populated pre-tenant upgrade. Never change old migrations. Existing realm import is skipped by Keycloak; it does not safely update an existing realm.

For a future original V14 upgrade: obtain explicit approval, take encrypted backups of both databases and configuration, audit current user-to-organization ownership (a previous rehearsal is not blanket approval for changed data), rehearse V15–V17 on a fresh restored copy, compare all historical records and membership access, review realm/client changes separately, schedule a maintenance window, then apply the approved plan. Never reset or delete original volumes.
