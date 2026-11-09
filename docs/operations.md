# Single-VPS operations
Phase 13 adds reviewed systemd timer templates, offsite freshness checks and success-only external monitoring pulses; see [operations setup](deployment-runbook.md). These are prepared files, not installed services. An external monitor and tested alert destination remain operator prerequisites. Published release deployments load `release.compose.json` so operations use the same immutable images as the serving release.
Use the explicit production project and environment file for every command. Never run generic teardown/reset against original development or QA stacks. Root/ Docker access is equivalent to secret access.

## Health, logs and resource signals
`bash infra/production/ops.sh workforce-prod /etc/workforce/production.env status` prints service state and aggregate Flyway/job/export/notification counts, next due work, expired leases and database sizes. No messages, recipient addresses or payloads are selected. Default expired-lease diagnostic threshold is five minutes; keep it aligned if runtime leases change.

`bash infra/production/monitor.sh workforce-prod /etc/workforce/production.env /var/backups/workforce` exits nonzero for missing/unhealthy services, no successful local backup in 26h, >=80% backup disk or <512 MiB available host memory. It also checks root/Docker disk, five-minute load, public HTTPS/OIDC issuer, overdue jobs, expired leases and terminal SAP/notification failures using aggregate read-only SQL. Wire the existing systemd monitor timer and success-only external heartbeat before launch. No external alert delivery is configured/tested. Inspect Docker restart/OOM counts and docker stats; an unhealthy container is not automatically restarted merely because of health status. restart:unless-stopped handles exited processes.

Spring liveness/readiness and Keycloak port9000 are private. Caddy loopback health8081 is not published. Frontend health and private Mock SAP health are available internally. Only proxy80/443 are mapped. Docker local logs rotate at 10MiB x3 per container; host journal also needs bounded retention.

Caddy issues a request ID and replaces untrusted forwarding headers. Its access/default JSON format omits request objects and response headers, including URI/query/cookie/token data. The BFF/backend's own established correlation remains separate where not propagated; this is not a distributed trace system. Do not enable verbose HTTP/auth/SMTP debug logging. Retain only safe statuses/IDs/timings; review export/notification operational logs for privacy.

Core Caddy does not include the third-party rate_limit module. Body and read time limits, Keycloak brute force and existing subject request limits are configured; these do **not** establish an anonymous per-IP quota. Before public registration, choose and test modest host/edge abuse protection and firewall rules without adding paid infrastructure. See [Caddy module classification](https://caddyserver.com/docs/modules/http.handlers.rate_limit). Do not claim that headers or hidden buttons prevent abuse.

## Updates and rollback
Build/test exact target images off the serving VPS. Back up both DBs/Mock SAP and independently preserve secrets/config. Record running image digests and migration level. Use a maintenance window, stop writers, review additive Flyway changes, and start the new backend only after explicit migration approval. Do not edit applied migrations, enable automatic mutable-tag pulls or automatically set legacy tenant confirmation.

Rollback code only if the previous artifact is compatible with the current schema/state. Otherwise restore into new volumes from the coordinated backup and verify before switching ingress. Never downgrade Keycloak against an upgraded database without its documented compatibility/restore plan. A failed rollout is not permission to erase data.

## Common incidents
- Login/redirect error: check fixed public issuer/client callbacks, forwarded-header trust, public HTTPS and private backchannel readiness, synchronized clock and matching client secret; see Keycloak guide.
- SAP FAILED: inspect safe code, attempt count and next attempt. Permanent business/auth failures need correction; do not force a new identity. UNKNOWN/expired claim: reconcile using the existing identity; do not blindly resend.
- SMTP outage: check private connectivity, verified sender, auth/TLS configuration and safe notification attempt status. Avoid printing payloads. Ambiguous acceptance can duplicate physical delivery.
- PostgreSQL unavailable: API readiness drops; restore connectivity before restarting workers. Persisted jobs/notifications survive process loss. Diagnose disk/OOM/restart before restarting repeatedly.
- Resource pressure: measure Docker memory/CPU, host available RAM/disk and DB connections. Reduce batch/concurrency or adjust reviewed budgets; do not invent user-capacity limits.
- Stop/restart: explicit `docker compose ... stop` or `restart SERVICE`, preserving volumes. Verify health after restart. Mock SAP has graceful connection draining; JVM shutdown and Docker init forward termination signals.
- VPS loss: obtain offline keys/config and offsite archive, initialize a new reviewed host/volume set, restore, validate and only then update DNS. This runbook is prepared; actual cloud loss/offsite restore has not been exercised.
