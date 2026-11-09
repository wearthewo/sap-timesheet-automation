# Encrypted backup and restore runbook
12D adds an operator-only backup profile. Phase 13 prepares opt-in systemd scheduling and an offsite wrapper; nothing is installed or sent automatically until an operator configures/enables it. Follow the [operator runbook](deployment-runbook.md). Local and remote checksums must pass before offsite success is recorded. A local restore rehearsal is not offsite disaster-recovery proof.

## Consistency and contents
Workforce and Keycloak share a PostgreSQL 17 instance with separate databases/owners. `ops.sh backup` stops the five application writers/proxy (only those previously running), retains PostgreSQL, dumps both databases and copies Mock SAP state, then starts the prior writers even on failure. This intentionally trades brief downtime for a consistent cross-service point. Do not run another backend/worker against these databases during backup.

Archive contains two custom-format pg_dump files, per-public-table count/content-hash manifests (including Flyway and memberships), Mock SAP JSON, format metadata and SHA256 checksums. Roles are recreated by the reviewed database initialization; no global password-hash dump is stored. Dumps/plaintext exist only in the helper's bounded tmpfs. The tar stream is encrypted by age before being written to BACKUP_DIR; partial files are never considered successful. The encrypted archive and checksum are retained, not XLSX downloads.

Use offline-generated age keys. Public recipients live on the VPS; keep private recovery keys and application/database secret configuration in an independent secure store. The private key is absent from routine backup mounts. The one-shot helper receives DAC_READ_SEARCH to read owner-only Mock SAP files through a read-only source mount, and CHOWN for restore ownership; it is not a long-running application service. Losing the key means losing recovery. Caddy certificates can be reissued; local CA keys are synthetic-only. Preserve DNS/provider access, host configuration, exact image digests and Git revision in the recovery plan.

```sh
docker compose -p workforce-prod --env-file /etc/workforce/production.env -f infra/production/compose.yaml -f infra/production/backup.compose.yaml build backup
bash infra/production/ops.sh workforce-prod /etc/workforce/production.env backup
```
Configure a root-owned systemd oneshot/timer (WorkingDirectory = reviewed checkout, ExecStart = that command, daily off-peak, Persistent=true). Use OnFailure to an approved operator notification mechanism; none is installed or contacted here. Run monitor.sh periodically, check journal/exit code and last-success age. A timer alone is not alert delivery. Keep only one scheduled backup; flock prevents simultaneous local backups.

## Offsite
`offsite.sh ARCHIVE DESTINATION` copies only encrypted archive/checksum through SSH with strict pinned host-key verification and a separate restricted key (see script variables). Destination must be operator-provided storage outside the VPS. No destination/account was purchased or tested. Verify remote checksum, remote retention and restore on another host before calling this disaster recovery. Local 14-day deletion is not remote retention and must not replace capacity planning. Restrict archive permissions even when encrypted.

## Restore: empty, isolated target only
1. Disable all ingress/writers. Select a **new** project/volumes and reviewed environment; never use down -v on original or QA environments.
2. Initialize only PostgreSQL on the empty target so the two owners/databases exist. Supply archived/coordinated secrets.
   Confirm password-authenticated connectivity from the backup container, not only local `pg_isready`. A disk-full interruption during first initialization can leave a locally healthy server with incomplete network access rules. A connectivity failure must stop recovery; preserve that target for diagnosis and use another explicitly approved empty target. Never reset or relax authentication on an existing database to force a restore.
3. Mount the chosen encrypted archive, public recipients and temporarily the offline identity file; use the restore override. Run:
```sh
bash infra/production/ops.sh workforce-prod-restore /etc/workforce/restore.env restore workforce-YYYYMMDDTHHMMSSZ-NNN.tar.age
```
The script refuses any existing public table or Mock SAP file, requires an explicit target confirmation, validates archive/checksum and allowed flat filenames, then pg_restore --single-transaction per database. It compares every public table's row count and canonical content hash before reporting success. If the second DB fails, the first may already be restored: there is no cross-database transaction. Preserve evidence and use another empty target; do not drop/reset a populated target automatically.
4. Start compatible Keycloak/Mock SAP/backend/frontend/proxy only after manifest verification. Recheck Flyway versions, tenant memberships, login/logout, approved/export history, worker leases/UNKNOWN reconciliation and Mock SAP duplicate identity behavior.
5. Remove the temporarily mounted private key. Do not expose restored services publicly until recovery validation passes.

A restored pending/expired SAP claim must reconcile before resending; SMTP acceptance may have happened after the backup and can cause duplicate mail. Disaster recovery cannot reconstruct external acceptance from a local DB alone. Mock SAP persisted state is included to avoid losing its duplicate detection; real SAP would remain an independent external authority.

Nightly backups imply up to roughly one day of data loss between successful runs, plus any failed interval. Restore time and offsite RPO/RTO are **not guaranteed**. Tmpfs size/content-manifest aggregation must be measured for real database sizes. Large databases may need a different streaming verification strategy and approved storage budget.
