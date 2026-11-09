# Phase 13 runtime remediation — 25–26 October 2026

Follow-on evidence: [frontend runtime remediation](phase13-frontend-runtime-remediation.md)
records the assembled distroless image's 0 HIGH/0 CRITICAL scan and focused
compatibility results, including the unresolved baseline Keycloak console error.
The [private-demo implementation record](phase13-private-demo-implementation.md)
documents the subsequent policy mechanism and Java coverage work. No individual exception is
approved by this historical checkpoint; the historical Caddy/PostgreSQL/backup evidence below
is unchanged and was not unnecessarily rebuilt or rescanned.

Parent: `8aa47fccafea1471d7756d6b75da1a4f8e1862f2`; branch:
`feature/phase12-production-readiness`. This checkpoint changes the proxy and
PostgreSQL/backup helper builds and fixes a demonstrated forwarding-header defect. It does not alter business
code, migrations, worker schedules or existing local deployments. **Deployment
remains NO-GO. No vulnerability exception is approved.**

The [453-record baseline inventory](phase13-runtime-findings.csv) is retained
unchanged as historical evidence. Candidate findings must not be subtracted from
unmodified production images. The following results use AMD64 and Trivy 0.76.0,
with the existing DB updated `2026-10-25T01:20:33Z`; no ignores, VEX overrides,
unfixed filters or skipped OS/language analyzers.

## Caddy: six HIGH occurrences and actual exposure

| Advisory / component | Installed baseline | Fixed version used or available | Exposure assessment |
|---|---|---|---|
| CVE-2026-111497 / brotli-libs | 1.2.0-r0 | 1.2.1-r0, used | Crafted Brotli decoding. Production Caddyfile has no Brotli decoder; no claim that all library consumers are unreachable. |
| CVE-2026-78669 / x/net | 0.59.0 | 0.60.0, used | HTTP/2 SETTINGS CPU exhaustion; directly relevant to public ingress. |
| CVE-2026-111943 / Go stdlib | 1.26.8 | 1.26.10 or 1.27.3; latter used | Certificate-chain validation weakness. Relevant to outbound certificate validation; not an inbound client-certificate route in the present configuration. |
| CVE-2026-78667 / Go stdlib | 1.26.8 | 1.26.9 or 1.27.2; 1.27.3 used | Crafted Range handling DoS; current proxy does not configure file serving. Exact vulnerable handler reachability not demonstrated. |
| CVE-2026-78669 / Go stdlib | 1.26.8 | 1.26.9 or 1.27.2; 1.27.3 used | Same HTTP/2 advisory, separate installed component occurrence. |
| CVE-2026-97031 / Go stdlib | 1.26.8 | 1.26.9 or 1.27.2; 1.27.3 used | Malformed TLS ECH handling; TLS is exposed, but the affected ECH path has not been proved enabled. |

Sources: [Go HTTP/2 advisory](https://pkg.go.dev/vuln/GO-2026-6611), exact
scanner/vendor links in the baseline CSV, and [Caddy's source-build guide](https://caddyserver.com/docs/build).
Rebuilding standard Caddy 2.11.7 with supported Go 1.27.3 and a pinned x/net
override is feasible. This is a repository-maintained build, not an upstream
binary release. Existing image roots remain pinned; a digest-pinned Go build
stage and complete checked-in module checksums were added. No custom plugin or
runtime privilege was added. Brotli's signed package is exact-version pinned.

The first bounded build reached its 600-second limit during compilation after
module verification. One subsequent bounded build reused those caches and
completed. The initial assembled candidate
`sha256:712bd4b5b28917f7f27b45a79c73a0b2075e0dc9d2ebf40a3a401053cc9a3c4c`
scanned successfully: **0 CRITICAL, 0 HIGH, 2 MEDIUM, 3 UNKNOWN**.
The production Dockerfile now consumes the reviewed lockfiles rather than
running `go mod tidy` during release builds. Final-image results are recorded
below after its compatibility check.

Remaining candidate findings are retained: musl/musl-utils 1.2.5-r23,
CVE-2026-107891 UNKNOWN (fixed 1.2.5-r24); zlib 1.3.2-r0,
CVE-2026-85091 MEDIUM (fixed 1.3.2-r1); chi/v5 5.3.2,
CVE-2026-114437 MEDIUM (fixed 5.4.0); x/crypto 0.57.0,
GO-2026-5932 UNKNOWN (no fixed version in this report). Trivy warned that its DB
had no details for CVE-2026-107891. UNKNOWN does not mean safe; refresh and review
these advisories before launch. The existing gate is unchanged.

### Confirmed compatibility defect

The actual TLS proxy check received no `X-Forwarded-Proto` at its synthetic
upstream. `header_up -X-Forwarded-*` is applied after assignments, deleting the
trusted headers too. The correction moves wildcard sanitization to
`request_header` before `reverse_proxy`, retaining canonical trusted values and
the existing Origin, authorization and routing boundaries. No assertion was
weakened. This is a configuration defect exposed by runtime verification, not
evidence of an application authentication failure or proof the Go upgrade
introduced it.

## Keycloak: two HIGH JDK findings remain

Both findings apply to the installed RPM
`java-21-openjdk-headless 1:21.0.12.1.1-1.2.el9` in the pinned Keycloak26.8.1 root.

- **CVE-2026-91403:** distrusted certificate-chain issue. Red Hat documents an OS
  trust-store mitigation for nonportable RPM builds. The inspected CA package is
  `2026.2.92_v9.0.319-90.3.el9_8`; previous evidence identified the Java cacerts
  link to that OS store. This qualifies the risk, but does not remove the scan
  occurrence or prove every deployment uses the correct store. [Vendor analysis](https://access.redhat.com/security/cve/cve-2026-91403).
- **CVE-2026-91415:** OpenJDK 2D graphics processing can affect availability and
  data integrity/confidentiality with crafted input. No application font-upload
  path was identified; that alone does not prove transitive graphics processing
  absent in Keycloak. The earlier report's font-only phrasing was too narrow.
  [Vendor advisory](https://access.redhat.com/security/cve/cve-2026-91415).

A fresh bounded query of public UBI9 repositories returned **the same JDK and
CA-package versions**, not a patched JDK RPM. Query image:
`registry.access.redhat.com/ubi9/ubi:9.8@sha256:217dd5692d369799d34d09ffb6f5f3404efebc7900834490fdf9ae92fd6721d4`.
Command: `dnf --setopt=timeout=20 --setopt=retries=1 --showduplicates list available java-21-openjdk-headless ca-certificates`.
The unregistered-entitlement warning did not prevent successful public-repository
results (exit0). No application container was changed.

Keycloak officially supports Java21 (and17/25); switching major Java versions is
unnecessary. Temurin21.0.13+9 binaries exist, while the inspected Jammy container
tag was unavailable and Noble still identified21.0.12.1. A checksum-pinned
custom Java21 runtime is a possible next experiment, **not a compatibility-tested
replacement**: it must retain kc.sh, CA trust, optimization, SMTP, OIDC and
shutdown behavior. No known-unpatched Keycloak rebuild was repeated. [Supported
configurations](https://www.keycloak.org/server/supported-configurations).

## PostgreSQL: patched helper verified and adopted in repository definitions

All baseline **1 CRITICAL/25 HIGH** detections are in gosu's Go1.24.6 inventory,
not evidence of26 remotely exploitable PostgreSQL server vulnerabilities.
Gosu drops privileges at startup and exposes no network listener. Upstream
explicitly distinguishes unused Go APIs from reachable helper behavior;
its exclusion wrapper was **not** imported into our gate. [Upstream policy](https://raw.githubusercontent.com/tianon/gosu/1.20/SECURITY.md).

Latest gosu1.20 fixes its own user-parsing issue, but its published binary still
uses Go1.24.6. A local candidate compiled the unmodified1.20 sources with pinned
Go1.27.3 (`GOTOOLCHAIN=local`, CGO disabled, module verification and readonly
module resolution), replacing only `/usr/local/bin/gosu` in the existing
PostgreSQL17 root. Exact source commit:
`29b939aa5bd4dc588af765b8248f6e39150d2565`; GitHub API tarball SHA256:
`d296a638f91b49c75344b212f1a24bed4a5b44acf3da9a7f2a41abfea851d4ee`.
[Release](https://github.com/tianon/gosu/releases/tag/1.20).

Candidate ID:
`sha256:43bd816f2255c187a20a056c2c395825cb396158950d17b07764a6518252a941`.
Full scan: **0 CRITICAL, 0 HIGH, 1 MEDIUM, 3 UNKNOWN**. Residuals:
musl/musl-utils1.2.6-r2/CVE-2026-107891 UNKNOWN (fixed1.2.6-r3),
zlib1.3.2-r0/CVE-2026-85091 MEDIUM (fixed1.3.2-r1),
x/sys0.1.0/CVE-2026-39824 UNKNOWN (fixed0.44.0).

Five component scenarios passed in an isolated tmpfs database: health under
production limits; original workforce/keycloak database initializer and
least-privilege roles; gosu privilege dropping and database settings; decimal
pg_dump/pg_restore; graceful shutdown without OOM. No original data was opened.

The final reproducible `postgres.Dockerfile` and `backup.Dockerfile` use the same
locked helper build, unmodified vendored upstream source and preserved Apache-2.0
license. Their final images passed the focused checks and complete scans below.
Production Compose now builds `workforce-postgres`; the release workflow scans
and publishes it under the Workforce namespace and records its own immutable
digest. The manifest rejects an unpatched upstream substitute and still rejects
a PostgreSQL18 image. Deployment also checks the PostgreSQL root against the
reviewed source lock. Existing volumes, entrypoint, initializer, major version,
resource limits and database format are unchanged. Restore continues through
the same backup/Compose mechanism with the patched helper image; no production
restore or original-data upgrade was performed.

This replaces the former six-custom-plus-one-official release inventory with
seven scanned custom image references. It intentionally rejects old synthetic
release manifests containing an official PostgreSQL reference. No production
release has been published; update installed operator tools from this reviewed
revision before any future deployment. Never relabel the custom image as an
official image. The existing development/QA containers remain on their original
images. Exact historical CVEs/versions/fixes remain in the baseline CSV; none
was hidden or waived.

## Frontend: qualified OS detections, unchanged gate

The completed original frontend scan remains **1 CRITICAL/56 HIGH**, with zero
findings in its detected Node-package inventory. It was not needlessly rerun.
The CRITICAL **CVE-2023-45853** is reported on zlib1g1:1.2.13.dfsg-1, while the
affected code is MiniZip `zipOpenNewFileInZip4_64`, not ordinary zlib inflate.
Debian says contrib/minizip is not built into this binary; the image has no
minizip package. Bookworm's separate minizip1.1-8+deb12u1 is fixed; installing it
would not patch this scanner label. Trixie's zlib source is fixed, but the
already scanned alternative retains43 HIGH OS findings. [Debian tracker](https://security-tracker.debian.org/tracker/CVE-2023-45853).

The56 HIGH occurrences are48 util-linux package occurrences across
CVE-2026-53613/53615/76642/78408/78409/78410, plus gzip/CVE-2026-41992,
libacl1/CVE-2026-54369, libsystemd0 and libudev1/CVE-2026-16742,
ncurses-base/ncurses-bin/libtinfo6/CVE-2025-69720 and perl-base/CVE-2026-9538.
Exact installed versions and absence of scanner-listed Bookworm fixes are in
the CSV. Node runs nonroot, read-only, without capabilities or a homed service;
these are exposure reductions, not blanket proof of non-exploitability.

Distroless Node24 would eliminate many utilities but has no shell for the
current secret loader. Alpine changes glibc to musl. Neither is a tested drop-in
replacement. Do not mix distribution packages or remove libraries merely to
make scans green. A focused runtime/bootstrap/native-module compatibility
experiment is the next step if a supported patched root remains unavailable.

## Exception proposals — for review only

No exception file, ignore rule, automatic approval or threshold change is
introduced. An owner must review each proposal against the **final image
digest**, not a mutable tag. Suggested expiry:14days or the next image rebuild,
whichever is sooner. Any newly reachable path invalidates the proposal.

| Finding | Evidence required for a proposed exception | Current decision |
|---|---|---|
| Debian CVE-2023-45853 | Vendor binary qualification, final SBOM proving no minizip/pyminizip, no bundled vulnerable ZIP writer; documented owner and expiry | Strong non-affected-binary proposal; **not approved** |
| Keycloak CVE-2026-91403 | Final-image CA RPM/version, exact Java trust-store path/hash, affected CA removal and certificate-chain regression | Mitigation proposal; **not approved** |
| Keycloak CVE-2026-91415 | Vendor patch or convincing reachable-code analysis of all extensions/input paths, plus reviewer risk acceptance | Insufficient evidence; remain blocked |
| Frontend utility/OS HIGHs | Per-package binary/symbol mapping and request-to-operation analysis under actual runtime restrictions | No blanket exception; remain blocked |
| Original gosu Go detections | Unfiltered symbol/call-graph analysis per advisory, or complete reviewed helper rebuild integration | Rebuild selected and integrated; no exception required for the removed detections |

## Verification record and exact-revision GitHub handoff

All Docker candidate builds, archive exports, pinned-image pulls, public UBI
query and disposable component runs required outside-sandbox Docker access.
Native scanner and Node static checks used repository-scoped tools. No global
toolchain was changed. Raw logs, archives and temporary synthetic secrets are
ignored under `.verification/phase13-remediation`; no raw artifact is committed.

Targeted checks so far: `node --test infra/production/production.test.mjs infra/production/release.test.mjs`
— **30 passed,0 failed,0 skipped**. This retains the strict scan, exact-revision
CI/E2E and independent-approval assertions. No full backend or browser rerun.

Failure accounting: the first PostgreSQL scan started before archive export
completed and failed because its input was absent; rerunning after confirmed
export completed successfully. Initial proxy fixture startup failed because
the pinned Node image was only in build cache, not the runtime image store;
pulling that exact digest fixed setup. The next proxy execution exposed the
forwarding-header defect above. Failed setup projects were individually cleaned
up; these failures are not counted as passing checks.

**No GitHub run for this revision is claimed.** After authorized push, require core CI and E2E on the
exact new SHA, then review a fresh assembled-image scan for every release
component. Production publication remains blocked while any HIGH/CRITICAL
finding remains; earlier green application CI is not image security clearance.

## Final reviewed images and focused results

These are immutable **local image/config IDs**, not published registry manifest
digests. AMD64 was inspected and executed. No ARM64 runtime claim is made.

| Final recipe | Local image ID | CRITICAL / HIGH / MEDIUM / UNKNOWN | Compatibility |
|---|---|---|---|
| caddy.Dockerfile | `sha256:ee561df617e2793fbdf100be66b4a04fdcbfb1aab976d800cd5be0b065964ace` | 0 / 0 / 2 / 3 | 6 proxy scenarios passed |
| postgres.Dockerfile | `sha256:5ff8df7ee1408b8efa14381bf88b6370a2c5e6a2d117b467e2c39ff7061e4a5b` | 0 / 0 / 1 / 3 | 5 database component scenarios passed |
| backup.Dockerfile | `sha256:2a9fab20f16280fe3fe4f01637a835f2027cb14b3562040d98b029ab6007aa73` | 0 / 0 / 1 / 3 | toolchain/syntax/encryption smoke passed |

[All13 remaining candidate package/advisory occurrences](phase13-remediated-image-findings.csv)
are retained, including UNKNOWNs. All three complete reports pass the unchanged
`scan-verification.mjs` policy. That means no detected HIGH/CRITICAL within its
inventory, **not security clearance** or proof no undiscovered vulnerability
exists. The backup scan detects age1.3.2-r1 as an OS package; its detected Go
binary target is gosu. Do not infer comprehensive age dependency coverage from
that scan. Fresh release scanning and unresolved advisory review remain required.

Report SHA256s (raw files remain ignored):

- Caddy: `ab3bee44a69fe7ed9effd1903cc061e74993e88491b081fa90bae6fb48a60873`.
- PostgreSQL: `9528db9a33795b9c539a77310628559d49c006a7d9f22492c00917f26cb83219`.
- Backup: `888a817276dd57146dc05b458d0441ce0da59a5cae43157f81cdcb7ad3383ef0`.

Final proxy execution: `.verification/phase13-remediation/workforce-runtime-check-e4612854e091/result.json`.
Final PostgreSQL execution: `.verification/phase13-remediation/workforce-runtime-check-d70dae75a862/result.json`.
These evidence paths are local and ignored; the reported commands reproduce
the checks. The proxy used the actual production Caddyfile, UID1000, read-only
filesystem, dropped capabilities, original256MiB/0.5CPU limits, trusted test CA,
HTTP/2 and original health probe. It checked forwarding spoof removal, retained
Origin/canonical headers, TLS/HTTP redirect, public Keycloak route versus
`/admin` denial,200KB body rejection and graceful shutdown. Its optional relay
made public GETs to the existing app33000 login/health and Keycloak38180 discovery;
these also passed. No cookie, account login or data mutation was performed.
This is actual proxy integration, **not a repeated authenticated browser phase**
and not proof of public ACME issuance, production SMTP or real-domain OIDC.

The final database check retained the original1536MiB/1CPU limits,256MiB shared
buffers,80connections, initializer, two independent database roles and health
probe. It verified privilege dropping,7.25decimal dump/restore and clean exit0
without OOM. Storage was tmpfs; **no durability/restart claim** is inferred.
Backup smoke verified gosu1.20/Go1.27.3, PostgreSQL17.11 tools, age1.3.2, Bash
syntax and encryption/decryption of a synthetic string in tmpfs without mounts.
No general backup or Phase12F acceptance suite was repeated.

Exact focused commands (use scoped Node24 on this Windows host):

```sh
docker buildx build --platform linux/amd64 --load -f infra/production/caddy.Dockerfile -t workforce-proxy:security-reviewed .
docker buildx build --platform linux/amd64 --load -f infra/production/postgres.Dockerfile -t workforce-postgres:security-reviewed .
docker buildx build --platform linux/amd64 --load -f infra/production/backup.Dockerfile -t workforce-backup:security-reviewed .
node infra/production/runtime-candidate-check.mjs proxy sha256:ee561df617e2793fbdf100be66b4a04fdcbfb1aab976d800cd5be0b065964ace --live-readonly
node infra/production/runtime-candidate-check.mjs postgres sha256:5ff8df7ee1408b8efa14381bf88b6370a2c5e6a2d117b467e2c39ff7061e4a5b
node --test infra/production/production.test.mjs infra/production/release.test.mjs infra/ci/ci.test.mjs
node --check infra/production/runtime-candidate-check.mjs
docker compose --env-file infra/production/production.env.example -f infra/production/compose.yaml -f infra/production/backup.compose.yaml config --quiet
actionlint
bash -n infra/production/deploy-release.sh
git diff --check
```

The combined focused Node execution passed **81/81**,0failures/0skips. A later
production/release-only rerun after extending the pinned-root assertion passed
**33/33**; those are overlapping tests, not114unique tests. Actionlint, shell
syntax, Compose config and script syntax passed. Each image was exported with
`docker image save -o .verification/phase13-remediation/NAME-reviewed.tar workforce-NAME:security-reviewed`
(Caddy tag uses `workforce-proxy`) and scanned with the checksum-verified native
Trivy command:

```sh
trivy image --input .verification/phase13-remediation/NAME-reviewed.tar --cache-dir .verification/phase13b1/trivy-cache --cache-backend memory --parallel 2 --timeout 4m --skip-db-update --skip-java-db-update --scanners vuln --format json --output .verification/phase13-remediation/NAME-reviewed-scan.json
node infra/production/scan-verification.mjs .verification/phase13-remediation/NAME-reviewed-scan.json
```

All scans exited0; `GOMAXPROCS=2` was set. Module files and all six vendored gosu
source/license files were compared byte-for-byte with the verified upstream
archive. The production build uses immutable compiler/base digests and locked
Go modules; no compiler download fallback or dependency verification bypass.
No Java/Node application dependency, migration or worker schedule changed.

An offline metadata inspection of age emitted APK repository DNS warnings
because the throwaway container intentionally had no network; it still exited0
and reported the installed BSD-3-Clause package and executable. This was not an
update or vulnerability scan and is not treated as extra security clearance.

Final preservation readback: all17 pre-existing containers healthy; no disposable
component project remains. Available disk30.12GiB. Temporary component projects
had no named volumes, used tmpfs and were removed by their unique project name.
No original database, realm or volume was mounted or altered. No push, merge,
image publication, cloud operation or deployment occurred.

## Remaining release blockers and GitHub decision

**GO for a reviewed local checkpoint and separately authorized exact-revision
GitHub verification. NO-GO for private-demo deployment or public registration.**

1. Resolve Keycloak's two JDK HIGHs and the bundled Temurin JRE support/security
   concern; an OS-only Temurin scan is not comprehensive Java clearance. Await
   vendor-patched Java21 roots or compatibility-test a checksum-pinned supported
   JRE replacement. Do not repeatedly rebuild the same unpatched RPM.
2. Resolve frontend1CRITICAL/56HIGH detections with supported runtime changes or
   explicitly reviewed vendor-qualified decisions. None is approved here. Node
   slim/Trixie and shellless/musl alternatives have the constraints above.
3. Review residual UNKNOWN/medium findings and fresh vendor data. No automatic
   UNKNOWN-to-safe inference, including the scanner's missing musl advisory details.
4. After authorization to push this checkpoint, require backend/frontend/MockSAP/
   quality CI and zero-retry E2E/exact Mailpit results **on this exact SHA**. Prior
   green runs do not cover these changes. No full local backend/browser rerun.
5. Before separately authorized publication, rebuild/scan every final release
   image on GitHub, retain full reports and registry digests, and enforce the
   existing manual environment approval and all-HIGH/CRITICAL gate. The seven
   image release inventory now includes the helper-patched PostgreSQL image.
6. Real TLS/SMTP and hosting requirements remain the existing deployment checklist;
   none is provisioned or expanded by this checkpoint.

After separately authorized push, the existing workflows support manual feature
branch verification (commands documented only, **not executed**):

```sh
gh workflow run ci.yml --ref feature/phase12-production-readiness
gh workflow run e2e.yml --ref feature/phase12-production-readiness
gh run list --branch feature/phase12-production-readiness --commit <checkpoint-sha>
```

Confirm each run's `head_sha` equals the checkpoint and inspect every job and
artifact. Publication/deployment workflows remain protected, main-only manual
operations; do not dispatch them while the known security blockers remain.

## Reviewed checkpoint file inventory

Final staged review: whitespace checks passed;2/2Windows-style LF checkout tests
passed. All13 curated findings exactly match the final scan reports and image
IDs. All30 staged paths were checked for private artifacts and key/token
patterns; none found. Vendored source/checksum bytes retain LF. The original
453-record CSV and strict scan implementation are unchanged. Actionlint passed
again after the complete workflow review. These checks do not approve any
documented security exception. The commit containing this report is the local
checkpoint; its hash is reported after creation, with no push or deployment.

30 files, all runtime remediation, directly required release integration, focused
verification or evidence. No private scan archives, QA credentials, database
backups, generated images or application business files are included.

```text
.gitattributes
.github/workflows/production-publish.yml
historical provider-readiness report (now retired)
docs/phase13-remediated-image-findings.csv
docs/phase13-runtime-remediation.md
docs/phase13-runtime-security-review.md
infra/production/Caddyfile
infra/production/backup.Dockerfile
infra/production/caddy.Dockerfile
infra/production/caddy/README.md
infra/production/caddy/go.mod
infra/production/caddy/go.sum
infra/production/caddy/main.go
infra/production/compose.yaml
infra/production/deploy-release.sh
infra/production/gosu/LICENSE
infra/production/gosu/UPSTREAM.md
infra/production/gosu/go.mod
infra/production/gosu/go.sum
infra/production/gosu/main.go
infra/production/gosu/setup-user.go
infra/production/gosu/version.go
infra/production/images.lock.json
infra/production/postgres.Dockerfile
infra/production/production.test.mjs
infra/production/release.mjs
infra/production/release.test.mjs
infra/production/resolve-images.mjs
infra/production/runtime-candidate-check.mjs
infra/production/verify.mjs
```
