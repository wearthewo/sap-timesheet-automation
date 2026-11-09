# Phase 13 runtime security review - 25 October 2026

This is the historical baseline at `8aa47fc`. The subsequent
[runtime-remediation checkpoint](phase13-runtime-remediation.md) records patched
Caddy/PostgreSQL candidates, final scans and compatibility results. It also
clarifies that CVE-2026-91415 concerns broader OpenJDK2D processing, not only a
font-upload route. Keep the original453-record inventory for comparison.

**Private-demo deployment: NO-GO.** This is an inventory and exposure review, not security clearance or a suppression policy. Production pins and application behavior are unchanged.

## Exact inventory and scope

[Machine-readable findings](phase13-runtime-findings.csv) contains **453 package/advisory occurrences**, across the pinned Caddy, Temurin, Keycloak and PostgreSQL roots and the actual Phase12F frontend image. Each row includes exact image/target, advisory, severity, installed package/version, scanner-reported fixed version/vendor status, runtime assessment and advisory link. Medium/low/unknown findings are retained too. Duplicate CVEs across packages/images are not independent vulnerabilities.

Trivy0.76.0 used the DB updated `2026-10-25T01:20:33Z`. Container scanner digest: `sha256:2416d1f74045b57274df1a340eee6b510cacd73c66a6ccb8da092b117eb7d222`. No ignore file, skipped package directory, unfixed suppression, VEX override or changed release threshold was introduced. The CSV is a reviewed public package inventory, not a raw private verification artifact. It contains no credentials, database records or application payloads.

| Target | Critical | High | Medium | Low | Unknown | Evidence limit |
|---|---:|---:|---:|---:|---:|---|
| Caddy pinned root | 0 | 6 | 15 | 1 | 3 | Root image; final proxy still needs release scan |
| Temurin pinned root | 0 | 0 | 27 | 16 | 0 | OS inventory does not comprehensively identify bundled JRE CVEs |
| Keycloak pinned root | 0 | 2 | 55 | 35 | 0 | Final augmented image still needs release scan |
| PostgreSQL pinned root | 1 | 25 | 32 | 3 | 3 | Severe Go findings located in gosu |
| Actual frontend `e6326259263d...` | 1 | 56 | 87 | 83 | 2 | Complete scan; detected Node package findings: zero |

The existing full Mock SAP runtime scan remains historical13B.1 evidence, not a fresh final release scan. Backend/proxy/Keycloak/backup assembled release images were not rebuilt. No final GHCR release manifest exists.

## High/critical runtime assessment

- **Caddy Go:** CVE-2026-111943 (`stdlib v1.26.8`, fixes1.26.10/1.27.3); CVE-2026-78667, CVE-2026-78669 and CVE-2026-97031 (`stdlib v1.26.8`, fixes1.26.9/1.27.2); CVE-2026-78669 also affects `golang.org/x/net v0.59.0`, fixed0.60.0. The proxy terminates untrusted HTTPS/HTTP2, so network processing is in scope. HTTP2 SETTINGS exhaustion is directly relevant; exact Range, certificate-chain and ECH configuration/symbol reachability was not proven individually. Keep blocked. [Go HTTP2 advisory](https://pkg.go.dev/vuln/GO-2026-6611).
- **Caddy Brotli:** CVE-2026-111497, `brotli-libs1.2.0-r0`, fixed1.2.1-r0. Exploitation needs malicious decoder input. The Caddyfile does not configure a Brotli decoder; this does not prove every bundled utility unreachable. [Vendor advisory](https://access.redhat.com/security/cve/cve-2026-111497). Keep blocked.
- **Keycloak JDK:** CVE-2026-91403 and CVE-2026-91415, `java-21-openjdk-headless1:21.0.12.1.1-1.2.el9`, no fixed RPM listed by this DB. The former concerns distrusted intermediate CAs; the existing patched OS trust-store/symlink evidence is a qualification, not complete JDK clearance. The latter needs untrusted font rendering; no font-upload route was identified, but transitive reachability was not proved absent. [Font advisory](https://access.redhat.com/security/cve/cve-2026-91415). Both remain blocking.
- **PostgreSQL gosu:** all1critical/25high occurrences are `stdlib v1.24.6` in `usr/local/bin/gosu`; see individual CVEs and fixed versions in the CSV. This executable drops privileges at startup and does not expose an HTTP/TLS listener. The network-library advisories are not evidence of an exploitable SQL server. The CVE-2026-39836 Windows-only path is also not the configured Linux runtime. No binary call-graph/VEX exemption was established, and the strict gate still blocks the image. A newer supported PostgreSQL17 image or reviewed gosu rebuild is needed; do not remove privilege dropping.
- **Frontend util-linux:** eight package occurrences per advisory for CVE-2026-53613,53615,76642,78408,78409,78410. Exact versions are in the CSV (source2.38.1-5+deb12u3, bsdutils has epoch1). No fixed Bookworm version is listed. These involve mount hooks, namespaces, partition parsing and filesystem operations. The Node runtime is nonroot/read-only with all capabilities dropped, with no device/mount workflow; no remotely exploitable path was demonstrated. Source-package detection is not a proof that every shared library contains every affected symbol. No waiver.
- **Other frontend highs:** gzip1.12-1/CVE-2026-41992 (LZH decompression); libacl1 2.3.1-3/CVE-2026-54369 (symlink/privilege operation); libsystemd0/libudev1 252.39-1~deb12u2/CVE-2026-16742 (homed signature checks; homed is not a serving process); libtinfo6/ncurses-base/ncurses-bin6.4-4/CVE-2025-69720 (terminal parsing); perl-base5.36.0-7+deb12u4/CVE-2026-9538. No fixed version is listed. No web-input path to those operations was demonstrated; general Node privilege reduction is not proof of non-exploitability.
- **Frontend critical:** zlib1g1:1.2.13.dfsg-1/CVE-2023-45853. [Debian's tracker](https://security-tracker.debian.org/tracker/CVE-2023-45853) qualifies the binary as unaffected because vulnerable minizip code is not built. Preserve the scanner occurrence and vendor explanation; the current gate still blocks it. This is not a claim of an exploitable Node critical, and no blanket suppression was added.

For other severities, package presence and available fixes are recorded individually; runtime exploitability remains **unproven**, not implicitly safe. No exploit was run against the local application.

## Supported alternative images and patch availability

| Candidate / exact index digest | Observed result | Decision |
|---|---|---|
| Caddy2.11.7 moving rebuild `eb4d35b7452d1ab6171fbdbe8d9275b2301dad6dad92115d91e19fb9b24d510f` | Official release remains2.11.7; prior13B.1 rebuild scan still has five Go highs | A tag refresh alone is insufficient; no pin change |
| Temurin21.0.13_9-jre-jammy | Registry returned **not found** | Published21.0.13+9 binaries are not proof of a released container |
| Temurin21-jre-noble `35501d3f93173b420ac9001658eb68157e813316b865087e345c187fca422c4c` | AMD64 manifest still identifies21.0.12.1_1 | OS switch does not fix bundled JRE; not selected or runtime-tested |
| PostgreSQL17.11/Alpine3.24 `eee6c6e1b0894fa4fe0075eb054af9fd1cf39a35321c2d4110626cfe506411fb` | Completed scan retains1critical/25high gosu findings | No security-clearing drop-in improvement |
| Node24.22.1-trixie-slim `43b9ba1142b8dcf33fa8f922ca1be85714adc444e0586c7cf7726daaeabc1bea` | Completed AMD64 scan:0critical/48high total, of which43OS and5npm tooling;67medium/61low/1unknown | Removes/reduces some Bookworm findings but cannot pass gate; not selected |

Trixie's five npm tooling highs are brace-expansion5.0.9 (CVE-2026-102276/102278), undici6.28.0 (CVE-2026-109869/19534), http-cache-semantics4.2.0 (CVE-2026-93748). Existing production Dockerfiles remove npm entirely and start Node directly; a hypothetical Trixie serving image would still have the43OS highs. This candidate scan is not a completed frontend rebuild or compatibility test. Official Node supports Trixie/glibc; an Alpine/musl switch would additionally require native Next/sharp dependency and secret-wrapper regression, so was not guessed safe. [Official variants](https://hub.docker.com/_/node).

A reproducible Caddy rebuild with Go1.27.3/x/net0.60.0 plus patched Brotli, and a checksum-pinned21.0.13 JRE rebuild, are possible follow-up patch paths. They introduce a maintained custom binary supply chain and require build, proxy/TLS/ACME and Java regression evidence. They were not implemented or claimed compatible here. Prefer upstream supported patched images; [Temurin binary release](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.13+9) and [container rollout](https://github.com/adoptium/temurin/issues/317) are distinct.

## Frontend CRITICAL: code and patch detail

CVE-2023-45853 is reported against **zlib1g `1:1.2.13.dfsg-1`**, but the affected function is MiniZip `zipOpenNewFileInZip4_64` in `contrib/minizip/zip.c`: oversized filename/comment/extra-field lengths can overflow size arithmetic and a heap allocation. This is not the ordinary zlib compression/inflate API. The completed image inventory includes88OS packages, **no minizip package**, and no detected Node-package advisory. Debian explicitly qualifies Bookworm's source finding because contrib/minizip is not built into its zlib binary. That supports a vendor-qualified non-affected binary assessment, not an application exploit claim or comprehensive image clearance.

Available fixes differ by package: Debian's separate Bookworm **minizip1.1-8+deb12u1** is fixed, but installing it would add an unnecessary library and would not change the zlib1g scanner occurrence. Debian records zlib's upstream-source fix in **1:1.3.dfsg-2**, with Trixie's **1:1.3.dfsg+really1.3.1-1** fixed. The inspected Trixie candidate has no CRITICAL finding, but43HIGH OS occurrences remain. Do not mix Trixie packages into Bookworm or remove zlib without dependency/ABI verification. [Debian advisory and patch reference](https://security-tracker.debian.org/tracker/CVE-2023-45853).

The full CSV contains **2CRITICAL/89HIGH occurrences,41distinct severe advisory IDs** across five targets. The other CRITICAL is PostgreSQL's gosu/Go CVE-2025-68121; neither is silently excluded. Installed/fixed versions in the CSV are verbatim scanner values. Frontend targets use the immutable image ID rather than the native scanner's local TAR path; all229 package/version/fix/severity records match the native report exactly. An initial comparison including the different input-path label failed; comparison normalized by the verified same image ID passed. This was an evidence-label difference, not a missing vulnerability.

## Minimal-runtime compatibility review

This is a source/configuration assessment, not a claim that the alternatives were built or accepted. No image pin, entrypoint, health check, capability or scan gate was changed.

| Component / alternative | Potential reduction | Concrete compatibility constraint / next proof |
|---|---|---|
| Next.js / Mock SAP: official Node24 Alpine | Removes Debian utility inventory | musl differs from glibc; rebuild and verify native Next/sharp modules, CA trust, UID, shell secret loader and all authenticated/download paths. No drop-in claim |
| Next.js / Mock SAP: distroless Node24 Debian13 nonroot | Omits shell/package manager and many utilities | Current `with-secrets.sh` requires `/bin/sh`; shellless image would fail before Node starts. Requires reviewed native secret bootstrap, explicit Node path, UID/volume and module-ABI tests |
| Spring: distroless Java21 or jlink runtime | Can reduce unused OS/JRE modules | Current secret bootstrap and readiness probe require shell/bash. Verify JDBC, TLS/SMTP, POI XLSX/font support and all reflected modules; stripping java.desktop blindly is unsafe |
| Keycloak | Official image already follows a minimal UBI-style layout | Preserve vendor optimization, kc.sh, CA trust, JRE support and graceful shutdown. Replacing its JRE/base independently needs auth, verification/reset, SMTP, startup and shutdown proof |
| PostgreSQL | New official17 rebuild or narrowly patched gosu | Preserve privilege drop, initdb, locale/encoding, pg_dump/pg_restore and backup tooling. No major-version upgrade or data migration to chase scan counts |
| Caddy: static/minimal root | May eliminate unused Brotli/OS utilities | Does not fix statically compiled Go/x/net flaws. Current build uses setcap/adduser and health uses wget; new root needs equivalent identity, health, CA/ACME, persistence and TLS verification |

Distroless publishes [Node24/Java21 AMD64 images and states they contain no shell](https://github.com/GoogleContainerTools/distroless). This is a real alternative, but **not a verified substitute** for the current secret-handling contract. Keycloak's [supported optimized container pattern](https://www.keycloak.org/server/containers) should be retained. Minimal images reduce package surface; they do not establish that remaining runtime dependencies are patched.

Prioritize a reviewed Caddy Go/x/net rebuild or patched upstream image first, then vendor-patched Java/Keycloak. Only then evaluate a minimal Node runtime if no supported direct variant can pass the unchanged gate. Any new digest must be verified on AMD64 with secret loading, health, shutdown, TLS and component regression before replacing production pins. No paid image service or new infrastructure was introduced.

## Frontend scan completion and failure history

The earlier socket scan exceeded the caller bound. A complete JSON later appeared with the correct image ID and OS/language inventory, but process success had not been captured. The archive scan failed at a semaphore/deadline boundary. Copying the archive into an isolated scanner container then hit its90s copy bound before analysis. A bounded single-file-at-a-time container scan also failed; none is called clean. These containers were removed individually, without application/volume cleanup.

An initial native download pattern used uppercase `Windows`, while the official asset is lowercase `windows`; it retrieved only checksums and executed no scan. The corrected **official Trivy0.76.0 Windows archive** was verified against its published SHA256 before extraction:

- Archive checksum: `7b4762a5a5326cb4b83dfd2bb5d9aaf15a1ed867c75cad7dd8b38840a28e7be6`.
- Extracted executable SHA256: `51e50a34f8739424bc88282602d10945b25249ffdb51703fe3c9453c20217c42`.
- Existing frontend image archive SHA256: `2137b7348457eac0b6f7ddbfed2a84566097fcf96766318f5201abcb04e81018`.
- Report SHA256: `32cd7767cb2df6e17f275565b3688b5cfe522f2568d6a5ead70b9aaa411ca4d6`.

With `GOMAXPROCS=2`, the equivalent command below uses placeholders for local tool and evidence paths (replace them before use):

```powershell
TRIVY_EXE image --input FRONTEND_OCI_ARCHIVE --cache-dir TRIVY_CACHE --cache-backend memory --parallel 2 --timeout 4m --skip-db-update --skip-java-db-update --scanners vuln --format json --output FRONTEND_REPORT_JSON
```

**Exit0, completed analysis in about9seconds**, same fresh DB, all88OS packages and22detected Node packages. Its229 findings exactly match the recovered report and its image ID matches `sha256:e6326259263d0e1955f22b545f69bc43968438a5544e9118246d7c965e62cc0f`. Exit0 means scanner completion, **not vulnerability clearance**. Direct native archive access removes Docker Desktop's cross-filesystem path; the comparison supports a tooling/I/O-path issue, but does not isolate one exact Docker kernel or semaphore defect. No security analyzer or package class was disabled to obtain the result.

Raw reports, downloaded tools, archive and logs remain ignored in the ignored local verification directory. No QA database, volume or running application was modified. Linux CI final-image scanning must still succeed for the actual release build; this local candidate evidence cannot replace it.
