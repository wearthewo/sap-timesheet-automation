# Phase 13 - Contabo preparation and evidence

## Runtime-remediation update — 26 October 2026

See [the reviewed runtime checkpoint](phase13-runtime-remediation.md) and its
[13 retained candidate findings](phase13-remediated-image-findings.csv). The
AMD64 Caddy, PostgreSQL and backup definitions now rebuild patched Go binaries;
the final assembled images each have0HIGH/0CRITICAL detections. Caddy's six HIGHs
and gosu's1CRITICAL/25HIGH detections were removed without an ignore rule. A real
proxy test found and fixed trusted forwarding headers being deleted by a
wildcard;6proxy scenarios,5PostgreSQL scenarios and backup runtime smoke passed.
The production release inventory now carries seven Workforce image digests,
including patched PostgreSQL; it must not substitute an unpatched official image.

Focused verification:81/81 combined Node checks, a later33/33 overlapping
production/release check, Compose validation, actionlint and shell syntax passed.
The existing17containers remain healthy, with original data/volumes untouched.
No full browser/backend suite or general infrastructure exercise was repeated.

**Deployment remains NO-GO:** Keycloak's two JDK HIGHs, frontend1CRITICAL/56HIGH
vendor-qualified/OS detections, residual UNKNOWN review and exact-revision
GitHub/final-image verification remain open. No exception is approved. The
historical preparation and453-record baseline below are preserved; they do not
describe the newly patched candidate images. GO only for local checkpoint
review and separately authorized GitHub CI, not publication or cloud deployment.

Date: 25 October 2026. Branch: `feature/phase12-production-readiness`. **No server exists, no deployment or publication was performed, and no purchase is authorized by this report.** Selected target: Contabo Cloud VPS 4, AMD64, four shared vCPU, 8 GB RAM, 100 GB SSD, Ubuntu 24.04 LTS, EU, one-month billing. Initial scope is an IP-restricted synthetic portfolio/demo, not unrestricted SaaS.

## Decisions and checkpoints

| Checkpoint | Observed state | Remaining work |
|---|---|---|
| A - Git/CI | Latest remote core and E2E runs PASS at `444f4b4`; local 12F checkpoint `77c665e` preserved | Exact final revision needs authorized push/review and CI; not green by inheritance |
| B - runtime security | Base scans and complete native frontend scan reviewed;453 findings inventoried | Security release gate remains NO-GO; see exact inventory and runtime assessment |
| C - AMD64 | All six pinned root manifests have AMD64 children; existing candidate metadata inspected | Final assembled release build/scan inventory and GHCR digests do not yet exist |
| D - Compose/8 GB | Six services, only Caddy public, fixed memory ceilings, config validation PASS | Actual shared-CPU VPS measurements and cold/restart acceptance |
| E - Ubuntu | Executable operator runbook, SSH hardening and restricted deployment account prepared | Run only on a separately authorized new host |
| F - DNS/TLS | Two-host contract, callbacks, proxy boundaries and private-demo firewall documented | Domain/IP, actual DNS, certificates and renewal tests |
| G - SMTP | Secret-file/STARTTLS templates and controlled sender tests documented | Choose provider, verify Keycloak transport timeout behavior, TLS/sender/recipient delivery |
| H - backups/monitoring | Encrypted backup/offsite/systemd helpers and operational probes prepared; focused tests PASS | Configure independent storage/alerts, test actual offsite restore/alert delivery |
| I - CI/CD | Manual-only protected workflows, exact-source and per-job gates, scan/digest manifest, fenced host deployment prepared | Repository environment policy, final security-clear images, approved publication/deployment |
| J - documentation | Contabo runbook replaces the unfinished Hetzner draft; current checklist and this report | Review remaining blockers before spending money |

## Git and GitHub evidence

Local HEAD `77c665e1fa76f07164e5a70211cc757fa545aafc` contains the 13 reviewed Phase 12F files. Remote feature HEAD is still `444f4b4306fc212ca6bb0567c1f68df15ac836d6`; no fetch/reset/push/merge was performed. Existing uncommitted Phase 13 work was preserved and completed where safe. No business code, database migration, realm, worker schedule or concurrency setting changed in this checkpoint.

Live GitHub readback (not just historical documentation):

| Run/job | Result |
|---|---|
| [Core CI 40092541380](https://github.com/wearthewo/sap-timesheet-automation/actions/runs/40092541380), backend | PASS; **411 tests, zero failures/errors/skips**, package PASS |
| Same run, frontend | PASS frozen install, lint, typecheck, build, smoke and production dependency gate |
| Same run, Mock SAP | PASS tests and image build |
| Same run, quality | PASS |
| [Full-stack E2E 40095013367](https://github.com/wearthewo/sap-timesheet-automation/actions/runs/40095013367), business-workflows | PASS; **28 smoke, 16 browser**, one worker, zero retries |
| Aggregate Mailpit acceptance in that run | PASS **50 exact messages**: 14 timesheet reminders, 4 manager digests, 2 rejections, 2 approvals, 6 SAP FAILED, 8 SAP UNKNOWN, 14 finance reminders; **0 cancellations** |

The earlier failing run `40064934207` is not hidden. Its cancelled reminder lacked retained historical detail and cannot be retroactively certified. The corrected gate still independently proves any supported cancellation, rejects unexpected cancellation/FAILED, waits only the unchanged bounded deadline for pending deliveries, and verifies every SENT intent against recipient/content/deduplication. Its 48 focused CI cases pass locally again. No gate assertion or timeout was weakened.

## Runtime security findings

Scanner: immutable `aquasec/trivy:0.76.0@sha256:2416d1f74045b57274df1a340eee6b510cacd73c66a6ccb8da092b117eb7d222`. Vulnerability DB refreshed to `UpdatedAt=2026-10-25T01:20:33Z`; scans use AMD64, all severities and no ignore/unfixed suppression. Counts are package/advisory **occurrences**, not independent remotely exploitable bugs.

| Scanned immutable target | Critical | High | Medium | Low | Unknown | Result |
|---|---:|---:|---:|---:|---:|---|
| Current Caddy 2.11.7 root | 0 | 6 | 15 | 1 | 3 | BLOCKED |
| Current Temurin 21 JRE Jammy root | 0 | 0 | 27 | 16 | 0 | OS scan only; incomplete JRE binary assurance |
| Current Keycloak 26.8.1 root | 0 | 2 | 55 | 35 | 0 | BLOCKED |
| Current PostgreSQL 17 root | 1 | 25 | 32 | 3 | 3 | BLOCKED pending package reachability/fix review |
| New PostgreSQL 17.11/Alpine3.24 candidate `eee6c6e1...` | 1 | 25 | 31 | 3 | 1 | Not selected: same severe findings |
| Actual frontend `e6326259263d...`, completed native scan | 1 | 56 | 87 | 83 | 2 | BLOCKED; zero findings in detected Node package inventory |
| Node Trixie candidate `43b9ba1142b8...` | 0 | 48 | 67 | 61 | 1 |43OS+5npm highs; not selected |

- **Caddy public edge:** installed Go1.26.8/x/net0.59.0 retain CVE-2026-111943, CVE-2026-78667, CVE-2026-78669 (two occurrences), CVE-2026-97031. New DB also flags brotli-libs1.2.0-r0, CVE-2026-111497 (fixed1.2.1-r0). HTTP/2 resource exhaustion is relevant at ingress; trust-store and untrusted Brotli decoding need distinct exposure analysis. [Go HTTP/2 advisory](https://pkg.go.dev/vuln/GO-2026-6611), [Go certificate advisory](https://www.cve.org/CVERecord?id=CVE-2026-111943), [Brotli advisory](https://access.redhat.com/security/cve/cve-2026-111497). [Latest official Caddy release](https://github.com/caddyserver/caddy/releases/latest) remains2.11.7. The current tag resolves to `eb4d35b7...`, already investigated in 13B.1 and still carrying its five Go highs. No unverified tag substitution was made. Go1.27.3 is available in the [official release feed](https://go.dev/dl/?mode=json), and x/net0.60.0 addresses the recorded package finding. Require an official patched Caddy image or a separately reviewed reproducible dependency rebuild plus proxy regression and final scan. That rebuild was not invented or declared verified here.
- **JDK/Keycloak:** the Keycloak image pin is unchanged and current. The new scan classifies CVE-2026-91403 and CVE-2026-91415 as HIGH (the former was CRITICAL in the older DB). The prior fixed OS trust-store/symlink evidence is retained, not silently treated as a waiver. [Vendor Java2D advisory](https://access.redhat.com/security/cve/cve-2026-91415) requires untrusted font processing; no application font-upload route was added. Lack of such a route is not complete proof of non-reachability. Temurin's moving21-JRE image still contains21.0.12.1+1. [Temurin21.0.13+9 binaries](https://github.com/adoptium/temurin21-binaries/releases) are published while [image rollout status](https://github.com/adoptium/temurin/issues/317) is separate. Wait for/review the patched image or explicitly validate a checksum-pinned binary rebuild; do not substitute licensed Oracle JDK or claim a zero-OS-high result clears the Java runtime.
- **PostgreSQL:** the severe Go findings are in the packaged `gosu` executable rather than PostgreSQL SQL processing. The configured entrypoint uses it for startup privilege dropping, not an HTTP/TLS service. Keep findings visible and require a documented binary reachability/vendor resolution; no blanket ignore. The new root did not resolve them, so both production DB and backup image pins remain aligned with existing verification. No database was restarted to test a new image.
- **Node/Next.js:** Node24.22.1 remains the patched [latest Node24 LTS security release](https://nodejs.org/en/blog/vulnerability). Production npm removal and direct `node` startup remain intact. Next16.4.1, React19, Boot4.1.2 and Java21 are unchanged. Existing runtime scans retain Debian utility/library findings; nonroot/read-only/capability restrictions reduce exposure without waiving them. [Debian's zlib/minizip qualification](https://security-tracker.debian.org/tracker/CVE-2023-45853) must not be described as an independently demonstrated exploitable Node critical. No safe package fix is claimed for vendor-deferred/no-fixed-version entries.

The completed native frontend scan and historical Mock SAP scope are distinguished below; prior scans are not relabelled as fresh. See [exact CVEs, installed/fixed versions and applicability](phase13-runtime-security-review.md) and the [453-record inventory](phase13-runtime-findings.csv). Final backend/proxy/backup assembled images were not rebuilt here. The release workflow blocks all high/critical findings, unsupported OS, malformed/missing OS inventory and scanner failures. Even vendor-qualified findings still block until a future explicit, narrowly reviewed policy or patch resolves them. **No production security clearance.**

## AMD64 and resource inventory

`node infra/production/audit-images.mjs` confirms AMD64 child manifests for all six roots in `images.lock.json`, retaining immutable index pins. Private evidence: `.verification/phase13-contabo/image-manifests.json`. Registry inspection is not a fresh build/startup test.

| Existing local candidate | Architecture / user | Approximate decimal size | Limitation |
|---|---|---:|---|
| frontend `e6326259263d...` | linux/amd64, node | 271 MB | Phase12F production frontend, not a published registry digest |
| backend `939e08ce2249...` | linux/amd64, app | 382 MB | QA backend Dockerfile; does not prove current production wrapper |
| Keycloak `f9b754e73bc7...` | linux/amd64,1000 | 664 MB | Prior same-version security rebuild |
| Mock SAP `b94ff199419c...` | linux/amd64,node | 230 MB | Prior security runtime |

Caddy Dockerfile uses1000:1000; PostgreSQL starts root solely for official initialization/drop-to-postgres; backup helper has scoped read/chown capabilities and runs only on demand. Frontend/MockSAP execute Node directly, not npm. Every production FROM is digest-pinned; no image-build secret args were introduced. Production config mounts secrets at runtime from root-controlled files. Docker/Git ignore rules exclude verification state, environment files, private keys, backups and release overlays. A digest alone is neither a signature nor a vulnerability waiver.

Unchanged container memory ceilings: Caddy256 + PostgreSQL1536 + Keycloak1280 + backend1536 + frontend768 + MockSAP256 = **5632 MiB (5.5 GiB)**. Backup512MiB runs with writers stopped and uses512MiB plaintext tmpfs. CPU caps total6, not reservations, on four shared vCPU. Advertised8GB may be less than8GiB; measure actual MemTotal, disk latency/space, OOM/restarts and peak heap/native usage. No customer-count or throughput promise. Initial local free disk was~34.15GiB; no whole-stack build or cleanup occurred.

## Preparation changes and safety

- Reused existing production topology, secret wrappers, backup/restore tooling, job semantics and network boundaries. Only Caddy publishes TCP80/443. PostgreSQL, backend diagnostics, MockSAP and Keycloak administration remain private. Mailpit remains local/CI only.
- Replaced the unfinished untracked Hetzner runbook with [Contabo instructions](contabo-deployment.md), including reviewed-source installation, Ubuntu/Docker commands, second SSH session before hardening, provider+UFW firewall, private-demo allowlisting, DNS/TLS/callbacks, external SMTP, root-owned deployment authority and schema-aware rollback.
- Completed manual publishing/deployment workflows with official SHA-pinned Actions, minimal permissions, exact-revision CI/E2E **and required successful jobs**, independent environment reviewers, image scan gate and registry digests. No workflow was dispatched. GitHub environment controls must be configured and supported by the repository plan; otherwise use reviewed manual operations, not bypasses. Runner SSH ingress remains a deliberate operator decision, not globally open22.
- Host deployment serializes with backups, checks clean reviewed source/manifest/secret configuration, requires a coordinated verified offsite backup on upgrades, uses fixed project volumes, waits bounded health and changes the current pointer only on success. It records safe outcome/revision in a root-only log and tolerates a stale temporary symlink. There is no automatic schema downgrade or destructive rollback. First-deploy/unattended-host execution remains untested without a VPS.
- Offsite copy records success only after local and remote checksums. Timers are opt-in: nightly02:30UTC+jitter backup and five-minute monitor. Age private keys remain off-host except controlled restore. Remote retention must be configured at independent storage; it is not implemented by deleting remote objects here.
- Monitor now checks health, public HTTPS/OIDC issuer, root/Docker/backup disk, low available RAM, five-minute CPU-load pressure, FAILED/UNKNOWN SAP, failed notifications,15min overdue work and5min expired claims. Read-only SQL has a5s statement timeout and no recipient/payload fields. The existing production lease configuration is5min. A failed probe suppresses the success heartbeat so an independent monitor can alert, including VPS loss. No external alert/SMTP request was made.
- Protected final image scanning against malformed/partial evidence; improved LF checks for new scripts/systemd files. No global Git configuration changed.

## Targeted verification

Commands used repository-scoped Node24.22.1 (`.verification/runtime/node-v24.22.1-win-x64/node.exe`), Git Bash and existing actionlint1.7.7; no global toolchain change.

| Command/check | Observed result |
|---|---|
| `gh run list`, `gh run view ... --json jobs,headSha,conclusion`, selected job log summary lines; `git ls-remote origin refs/heads/feature/phase12-production-readiness` | PASS readback; exact results above |
| `node --test infra/ci/ci.test.mjs infra/production/production.test.mjs infra/production/line-endings.test.mjs infra/production/release.test.mjs infra/production/monitor.test.mjs` | **82/82 PASS**, zero failures/skips; earlier4/32-test focused runs are subsets, not additional unique coverage |
| `bash -n` for each `infra/production/*.sh` | PASS syntax |
| `bash infra/production/backup-guards.test.sh` | **4/4 PASS**, stubbed guards, no restore/database operation |
| `bash infra/production/offsite.test.sh` | **5/5 PASS**, synthetic temp archive/stub transports; no real offsite upload |
| `actionlint.exe` | PASS all workflow syntax/static checks; not execution |
| `docker compose --env-file infra/production/production.env.example -f infra/production/compose.yaml -f infra/production/backup.compose.yaml config --quiet` | PASS template composition, no startup or secret provisioning |
| Actual `monitor.sql` through `docker exec -i workforce-ci-493efe29fd43-postgres-1 psql -XqAt -v ON_ERROR_STOP=1 -U workforce -d workforce -f /dev/stdin` | PASS read-only query:3 expected synthetic terminal SAP failures; all other alert counters0; no state changed |
| `node infra/production/audit-images.mjs` | **6/6 AMD64 root manifests PASS**, no image pull/build |
| Bounded Trivy remote scans | Five completed including candidate; results above; do not interpret scanner execution exit0 as vulnerability clearance |
| `git diff --check` | PASS; expected markdown autocrlf notices do not affect Linux runtime files |

Docker/network/GitHub access required outside-sandbox execution. Filesystem edits, Node tests and actionlint used workspace/scoped tools. Shell checks used Git Bash. No backend/browser suite was repeated; the user explicitly requested avoiding duplicate acceptance. No local QA rebuild, volume reset, original V14 migration, realm update or cloud operation occurred. All17 existing containers were initially healthy; final preservation checks are recorded below.

## Blockers and required user actions

**Before buying/provisioning:** review unresolved Caddy/JDK/OS security findings and select a reproducible patch/triage strategy; approve final source review and eventual CI push separately; verify a **one-month** Contabo checkout quote. The [official plan specifications](https://docs.contabo.com/docs/servers-hosting/vps/) match4vCPU/8GB/100GB, but sales promotions are not the exact invoice. The `/en/vps/` request returned403; `/en-us/vps/` shows long-term offers. **EUR6.82 is not reverified.** Record VAT, setup, EU region, IPv4, renewal, domain, SMTP, offsite storage and alerting so the recurring total stays withinEUR15. No paid service was selected.

**During separately authorized deployment:** choose app/auth DNS and mail sender, configure keys/secrets and protected GitHub environments, publish scanned immutable images, provision clean volumes/realm, configure private tester allowlists, verify real TLS/SMTP, offsite recovery/retention and independent alert delivery. Validate actual 8GB shared-CPU resource behavior, reboot and schema-compatible rollback. Do not import original or QA identities.

**Before unrestricted SaaS:** additionally verify anonymous registration/reset abuse protection, incident/patch operations, privacy/retention policy, sustainable load and provider limits. Synthetic MockSAP acceptance does not establish real SAP integration; real customer data/email requires separate product/security review.

**Decision:** GO for review of repository preparation; **NO-GO to call the private deployment ready today** because security/complete final-image and exact-revision gates are open. **NO-GO for unrestricted public SaaS.** No server need be bought while those known release gates remain blocked. Existing localhost use and Phase12F acceptance remain available.

## Changed-file inventory and Git status

This reviewed Phase13 preparation is intended for one local checkpoint commit after `77c665e`; it does not rewrite Phase12F. The commit containing this report records the exact40-file inventory below. No push, merge or deployment is authorized. Runtime security blockers remain open regardless of checkpoint creation.

### Final bounded scan and preservation notes

Earlier Docker-socket and mounted-archive frontend scans timed out. Copying the archive into a disposable Linux scanner also timed out before analysis, and bounded serial container analysis failed. No failed scan is counted as clean. The official Trivy0.76.0 native Windows archive was then checksum-verified and used on the **same existing image archive and fresh DB**, without disabling analyzers. It completed with **exit0 in about9seconds**,88OS and22detected Node packages:1CRITICAL/56HIGH/87MEDIUM/83LOW/2UNKNOWN. Its229 findings exactly match the late report recovered from the earlier socket attempt, after normalizing the input TAR label to the verified immutable image ID. The full command, tool/report/image hashes and failed attempts are in [the security review](phase13-runtime-security-review.md). The cause is narrowed to the Docker/Desktop filesystem-analysis path; a specific internal semaphore defect is not proved.

The frontend CRITICAL is CVE-2023-45853 in zlib1g1:1.2.13.dfsg-1. Debian qualifies its binary as unaffected by the MiniZip ZIP-writing flaw; no minizip package is in the image inventory. Bookworm minizip1.1-8+deb12u1 and Trixie zlib1:1.3.dfsg+really1.3.1-1 provide fixes for their respective source packages. No waiver or mixed-distribution package replacement was made. Caddy's network-facing Go HTTP2 finding is the priority patch path. Trixie retains43HIGH OS findings, Noble retains the older JRE, and the tested Temurin21.0.13 Jammy image tag is not yet available. Shellless/minimal alternatives are assessed against actual secret-wrapper/health/ABI requirements in the security review, not falsely called drop-in compatible.

Fresh assembled MockSAP/backend/proxy/Keycloak/backup release scans remain outstanding. No broad rebuild or acceptance rerun was performed. All raw scan reports, tools, credentials and archives stay ignored; the committed CSV contains only reviewed package/advisory metadata.

Actual Java base scan also passed the strengthened report-schema/OS/severity parser; it still does not prove JRE vulnerability clearance. A preliminary proxy metadata format requested an absent `Entrypoint` field and reported a template error; the final proxy readback uses `Cmd` instead. Linux syntax checks and actionlint passed. No application or CI assertion was changed to conceal these limitations.

Reviewed modified/new paths (inherited drafts included):

```text
.dockerignore
.gitattributes
.github/workflows/ci.yml
.github/workflows/production-deploy.yml
.github/workflows/production-publish.yml
.gitignore
README.md
docs/backup-restore.md
docs/contabo-deployment.md
docs/operations.md
docs/phase13-contabo-readiness.md
docs/phase13-runtime-findings.csv
docs/phase13-runtime-security-review.md
docs/phase13-deployment-checklist.md
docs/phase13b1-dependency-security.md
docs/production-deployment.md
infra/production/audit-images.mjs
infra/production/deploy-release.sh
infra/production/init-secrets.sh
infra/production/line-endings.test.mjs
infra/production/monitor-cycle.sh
infra/production/monitor-health.mjs
infra/production/monitor-success.curl.example
infra/production/monitor.sh
infra/production/monitor.sql
infra/production/monitor.test.mjs
infra/production/offsite.sh
infra/production/offsite.test.sh
infra/production/operations.env.example
infra/production/ops.sh
infra/production/release-gates.mjs
infra/production/release.mjs
infra/production/release.test.mjs
infra/production/scan-verification.mjs
infra/production/scheduled-backup.sh
infra/production/ssh-hardening.conf
infra/production/systemd/workforce-backup.service
infra/production/systemd/workforce-backup.timer
infra/production/systemd/workforce-monitor.service
infra/production/systemd/workforce-monitor.timer
```

Earlier pre-native-scan preservation readback: all17 preexisting containers remained healthy, including the six-service localhost33000 environment. Application `/api/health` and Mailpit `/readyz` returned200. No temporary Contabo scanner remained running. At that earlier readback, disk free space was36,025,253,888 bytes (~33.55GiB); the reduction is ignored scanner DB/image-archive evidence, not application data deletion. Existing Caddy candidate metadata confirms linux/amd64, user1000:1000 and118MB size; no new proxy build is claimed.

## Final local checkpoint review

Branch: `feature/phase12-production-readiness`. Parent checkpoint: `77c665e`. **40 reviewed Phase13 files** comprise deployment/operations preparation, focused guards/tests and readiness/security documentation. No application business code, migration, production image pin or worker setting changed. The local checkpoint is permission to preserve this work, not to deploy. The exact hash is reported after Git creates the commit; use `git log -1 --format=fuller --stat` to inspect it.

Final focused results:82/82 Node infrastructure/CI tests,4/4 backup guard scenarios,5/5 offsite stub scenarios; shell syntax and actionlint PASS; production Compose config PASS. CSV-to-report reconciliation PASS for all453 occurrences (frontend source-path label normalized only after verifying identical image ID); package IDs, versions, severities and fixes unchanged. Existing completed scan evidence was reused rather than rerun. All17 existing containers remain healthy; app33000 `/api/health` and Mailpit38025 `/readyz` returned200. Available disk after scan/tool evidence:35,595,321,344bytes (~33.15GiB). No database/realm mutation, volume reset or QA restart.

The first staged whitespace check caught one extra blank line at the end of the new backup systemd unit; it was removed. Before commit: review complete diff and explicit staged inventory, `git diff --check` / `git diff --cached --check`, exclude verification files/backups/secrets, verify all tracked Linux inputs retain LF. Only this40-file preparation is included. Security clearance remains **NO-GO**, with2CRITICAL/89HIGH occurrences (41distinct severe advisory IDs) in the453-record inventory. Counts are detections, not41demonstrated exploitable application bugs.

### Exact next GitHub/release steps (not executed)

1. Review this checkpoint and the known blockers. Separately authorize any future feature-branch push. No push is performed now.
2. On that exact pushed revision, require core CI backend/frontend/MockSAP/quality and full `e2e.yml` business-workflows success, including exact Mailpit acceptance. Earlier remote green runs do not cover this commit. Inspect failed jobs/artifacts rather than suppressing or retrying blindly.
3. Resolve network-facing Caddy and Java/Keycloak patch paths, then remaining OS findings with supported, compatibility-tested images. Preserve the all-HIGH/CRITICAL fail-closed release policy; no approval to bypass it is implied by vendor qualifications. Obtain new final assembled-image scans and immutable digests.
4. After separately authorized source integration, configure independent GitHub environment approvals; the manual main-only publish workflow must verify exact-revision CI/E2E and every final-image scan before publishing. Nothing is published now. If the repository plan cannot enforce reviews, keep automated release disabled and use a separately reviewed operator process.
5. Only after security and release provenance gates close, authorize the one-month Contabo quote withinEUR15 total, clean host provisioning, tester-only firewall, domain/TLS, production secrets and verified SMTP. Configure and exercise encrypted offsite restore, independent monitoring and rollback. Measure actual8GB host resources and run authorized production smoke acceptance. MockSAP remains a simulator.

**Checkpoint conclusion:** coherent repository preparation may be committed locally with documented blockers. **Secure private-demo deployment remains NO-GO. Public SaaS remains NO-GO.** No cloud, DNS, publication, merge or deployment was performed.
