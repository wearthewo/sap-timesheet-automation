# V15 populated development database rehearsal

Verified locally on 2026-10-12. **PASS for this reviewed development snapshot.** The original development database was neither migrated nor reset. No application code or Flyway migration was changed during this rehearsal.

## Source and ownership decision

Source: `workforce-postgres-1`, application database `workforce`, PostgreSQL 17, successful Flyway versions 1 through 14. `pg_dump --no-owner --no-acl` ran with `default_transaction_read_only=on`. Its consistent snapshot was restored into a separate PostgreSQL container/database on an internal Docker network, without attaching the original volume.

The snapshot contains 73 application users, 57 employee profiles and eight projects. Ownership was verified before enabling the migration acknowledgement:

- All 73 users match the local Phase 1–8 QA fixture definitions, using employee numbers, roles, fixture subject patterns and reserved `example.test` addresses. Keycloak subject-to-username lookups resolved older accounts whose placeholder emails were shared across verification runs. No users or credentials were changed in Keycloak.
- The eight project codes are `PHASE1-DEMO`, `PHASE2-DEMO`, `MOCK-INVALID`, `MOCK-RETRY`, `MOCK-TIMEOUT`, `MOCK-TRANSIENT-503`, `REPORT-A` and `REPORT-B`. Their setup scripts create shared projects in the same development dataset.
- Relevant provenance: ignored local `setup-user.ps1`, `setup-phase2.ps1`, `setup-phase3.ps1`, `setup-phase4.ps1`, `setup-phase6.ps1`, `setup-phase7.ps1`, `setup-phase8-names.ps1`, and `infra/dev/reporting-fixtures.sql`. Current fixture scripts were inspected, not executed against the source.
- All dependent records follow the existing employee/project/timesheet/export/notification relationships. Every database constraint was validated after migration.

The verified mapping assigns this shared QA dataset to `00000000-0000-0000-0000-000000000001`, slug `legacy`. All 73 memberships retain their exact existing role; employee/user links and manager relationships remain unchanged. Distinct accounts with the same display name or placeholder email remain distinct identities.

**Ambiguity:** none remains about the fixture ownership of this snapshot. A real organization/legal name is not stored and was not invented; the migration retains `Legacy organization`. This evidence does not authorize mapping a different database or subsequently added, unexplained users to that organization. Older repeated fixture accounts, including ADMIN/FINANCE accounts, remain active as before; migration does not perform account cleanup.

`LEGACY_TENANT_CONFIRMED=true` was set only in the ignored disposable backend environment after the complete mapping was verified. The repository `.env` and original backend configuration were not modified.

## Preservation evidence

For every original table, rows were sorted by primary key, represented as JSON, and hashed with SHA-256. Post-migration comparisons excluded only the newly added `organization_id` field and new V15 history row. All pre-existing columns were included, including IDs, timestamps, versions, decimal hours, snapshots, external references, comments, notification contents and attempt histories.

| Table | Original rows | After V15 |
|---|---:|---|
| app_users | 73 | Identical |
| employees | 57 | Identical |
| projects | 8 | Identical |
| project_assignments | 120 | Identical |
| timesheets | 100 | Identical |
| timesheet_entries | 115 | Identical |
| timesheet_approvals | 92 | Identical |
| sap_timesheet_exports | 53 | Identical |
| sap_export_jobs | 53 | Identical |
| sap_export_attempts | 131 | Identical |
| notifications | 550 | Identical |
| notification_attempts | 550 | Identical |
| flyway_schema_history V1–V14 | 14 | Identical |

Total: **1,902 application records**, plus 14 prior migration records, preserved. Comparisons passed both immediately after migration and after access checks; the latter selected the original IDs to exclude additional disposable security fixtures. A final repeatable-read, read-only comparison against the original running database also matched every original count/hash and confirmed it remained at V14. This manifest-derived total corrects the interim progress message's arithmetic total of 2,002; individual table counts and hash comparisons were unchanged.

## Migration and tenant access results

| Scenario | Observed result |
|---|---|
| Actual Flyway startup against populated copy, confirmation=false | PASS: refused with ownership-review error; original copied rows unchanged |
| Actual Flyway startup, reviewed copy only, confirmation=true | PASS: V15 applied and recorded successfully |
| Hibernate schema validation / backend health | PASS |
| All legacy memberships / eleven business-table ownership columns | PASS: 73 correct active memberships, all original business rows in legacy |
| Database constraints | PASS: none unvalidated |
| Authenticated membership discovery and four migrated roles | PASS |
| Unauthenticated / missing organization requests | PASS: 401 / 400 |
| Legacy projects and employee's own timesheet | PASS: accessible, expected records returned |
| Direct-manager review / unrelated manager | PASS: 200 / 403 |
| EMPLOYEE, FINANCE, ADMIN manager override | PASS: denied |
| EMPLOYEE/MANAGER SAP worklist and reporting access | PASS: denied |
| FINANCE reporting and ADMIN SAP worklist | PASS: authorized |
| Historical notification privacy | PASS: owner can read; another recipient gets 404 |
| Second organization and organization-specific effective role | PASS: global legacy ADMIN role does not override other-organization EMPLOYEE membership |
| Guessed legacy project/SAP timesheet/XLSX identifiers from second organization | PASS: 404 |
| Second organization's SAP worklist / monthly report | PASS: empty worklist; every report count and hours total zero |
| Membership / organization suspension | PASS: next request denied |
| New identity provisioning | PASS: no automatic membership; legacy access denied |
| Organization-scoped project code uniqueness | PASS: same code permitted in separate organization |
| Cross-organization project assignment | PASS: PostgreSQL rejects reference |
| Resource organization reassignment | PASS: immutable ownership trigger rejects update |

The access runner completed **38 checks**; preservation, guard/startup and final report-value comparisons were additional checks. It used signed RS256 JWTs and a disposable JWKS issuer trusted only by the isolated backend, exercising normal Spring JWT validation and database membership authorization. Existing Keycloak users were queried read-only for provenance; browser login was not repeated. No authentication bypass was added to application code.

## Isolation, commands and evidence

Disposable resources were prefixed `workforce-v15-rehearsal`: PostgreSQL, the guard backend, the migrated backend and a small test JWKS server. SAP processing, notification evaluation and notification delivery were disabled. The internal network had no application connection to the development database, real SAP or SMTP. HTTP checks ran inside that network. All rehearsal containers and their anonymous database volume were removed after evidence collection; original services remained running.

Commands used the repository-scoped Node 24 executable and existing Java 21 backend image `workforce-ci-316268cb454e-backend`. Docker operations required outside-sandbox execution. No global toolchains were changed.

```text
node .verification/audit-legacy.mjs
node .verification/legacy-copy/rehearse.mjs restore
node .verification/legacy-copy/rehearse.mjs restore-only
node .verification/legacy-copy/rehearse.mjs mapping
node .verification/legacy-copy/backend.mjs guard
node .verification/legacy-copy/rehearse.mjs compare
node .verification/legacy-copy/backend.mjs migrate
node .verification/legacy-copy/access.mjs
node .verification/legacy-copy/rehearse.mjs compare
node .verification/legacy-copy/final-check.mjs
```

Temporary verifier issues corrected during execution: the initial PostgreSQL socket readiness check observed its initialization server, so restoration was repeated only after final TCP readiness; Docker Desktop did not expose the internal network's published HTTP port, so readiness/access checks used container-network HTTP; the membership assertion was corrected to the actual `id` DTO field; multiline PostgreSQL JSON output parsing was corrected. These were harness issues; no production defects or migration changes were required.

Ignored evidence directory: `.verification/legacy-copy/`. It contains the source dump, per-user ownership mapping, before/final manifests, guard and migration logs, migrated Flyway history, access results and zero-valued other-organization report. Treat the dump and mapping as sensitive local artifacts and do not commit them.

## Limits

This proves V15 migration/preservation and the tested tenant access rules for this snapshot. It is not a production rollout, a new GitHub CI run, a browser E2E rerun or a background-job recovery rehearsal. Workers were deliberately disabled to avoid executing copied work. Existing Phase 9 regression coverage remains separately documented. A future migration must review the then-current ownership inventory, preserve a fresh backup and handle any newly ambiguous records explicitly.
