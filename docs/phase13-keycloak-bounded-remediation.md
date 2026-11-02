# Bounded Keycloak remediation attempt and recovery — 29 October–2 November 2026

Starting revision: `62b89a28f5c1e42b8036ae0f0968be29698f425a`. Scope: CVE-2026-91415 (Java2D) and CVE-2026-102554 (Guava). No release policy or production definition is changed by the experimental attempt. No finding is approved.

**Final local decision, 2 November 2026: VERIFIED for entering final production release verification.** The corrected candidate passes one complete focused browser sequence (8/8 checkpoints), runtime permissions and Java TLS checks. Its complete image scan reports **0 CRITICAL, 0 HIGH, 0 UNKNOWN, 7 MEDIUM and 1 LOW**. This is technical candidate evidence, **not public-deployment approval**. The custom build still requires reviewed production adoption, release provenance/policy processing and host-dependent verification.

## Candidate decision and maintenance implications

During the original investigation, the official GitHub release API reported Keycloak 26.8.1 and the upstream [Guava issue](https://github.com/keycloak/keycloak/issues/54110) was open/blocked externally. This continuation did not repeat that upstream-release research. Guava's [33.7.2 release](https://github.com/google/guava/releases/tag/v33.7.2) fixes the deserialization allocation behavior; existing Keycloak resolved 33.6.0-jre. Existing evidence did not establish complete non-applicability; the candidate therefore applies the source-level fix.

[Corretto 21.0.13.0.11](https://github.com/corretto/corretto-21/releases/tag/21.0.13.0.11) includes the earlier Java2D security update and a subsequent Java security patch. Vendor archive SHA-256 `f3330bf440b8f4514b7b279b6145d4b4493dea9dda5f3a702b49c53c41bec1e4` was verified after one bounded download. The precise Temurin `21.0.13_9-jdk-noble` and Corretto `21.0.13.0.11-al2023` container tags were unavailable. The broader Corretto `21.0.13-al2023` manifest predates the latest vendor update; it is not assumed equivalent.

A custom candidate would use the unchanged vendor JDK archive on the pinned Amazon Linux base used by the official Corretto image and rebuild upstream Keycloak source with explicit Guava 33.7.2-jre/failureaccess 1.0.3 dependency management before Quarkus augmentation. This is **operator-maintained**, not an official patched Keycloak distribution or vendor-certified dependency combination. It requires retaining source/patch/package provenance; tracking future Keycloak, Quarkus, Guava, JDK and OS updates; repeating compatibility checks; reviewing shaded dependencies; and removing the override when upstream includes an appropriate fix. No loose JAR replacement is permitted.

The cached Keycloak source archive is reused and hash-verified. Experimental source, patch, Dockerfile and logs are isolated under ignored `.verification/keycloak-bounded-20261029/`. Upstream's documented server-only build has a combined approximately 20-minute outer budget, including setup and downloads, and constrained JVM/Node heaps. Upstream packaging uses its documented `-DskipTests`; successful packaging would not count as compatibility testing. Actual runtime/browser/TLS checks and a complete candidate scan are required before adoption. No production definition will be changed merely because compilation succeeds.

## Provenance and interruption handling

- Cached source archive SHA-256: `f7763d73c33a2d345227522aa51a5ead3b7f0fde2a807704933174b8a3628b5c`.
- Source-level Guava/failureaccess patch SHA-256: `febe08566816b41168f02f3c88f7144ad6c1bec9ea7ef226893a42ee58f99ac1`.
- Experimental Amazon Linux base: `amazonlinux:2023@sha256:b156298161e9ab6392cda59081d016866601f7213a28ec21270d3a75c72f2170`, selected for `linux/amd64`.
- Exact-tag [Corretto changelog](https://github.com/corretto/corretto-21/blob/21.0.13.0.11/CHANGELOG.md) records CVE-2026-91415 and CVE-2026-91403 in `.0.10`, followed by CVE-2026-114820 in `.0.11`. Archive metadata confirms `OS_ARCH=x86_64`, Linux, vendor Amazon.com Inc., and runtime `21.0.13+11-LTS`. Vendor SHA-256 was checked; detached-signature verification is not claimed.
- The original BuildKit record `vbrdlft8chjvgutcn4c0kgg9u` says **Canceled: context canceled**, before Java compilation. Including builder bootstrap, its log spans approximately 196.35 seconds. The interrupted attempt was resumed once using the same builder/cache with a 1,000-second remaining bound; it was not a retry of a compiler or compatibility failure.
- The resume preflight initially rejected a PowerShell UTF-16 diagnostic log read as UTF-8. BOM-aware decoding corrected the harness before a Docker build was started. Original logs remain intact.
- The original experiment builder is limited to 3 GiB and two CPUs. No global builder selection or application environment is changed. Builder/cache resources are retained; this continuation does not claim they were stopped. The final small image delta used the existing Docker-engine builder explicitly, without repeating compilation.
- The realm import, secret wrapper and both login-theme files were hash-compared with production and are unchanged. The experimental source dependency override is not an adopted production dependency change.

The base digest and vendor archive are pinned. DNF resolves signed packages from the Amazon Linux repository at build time; the build log records installed RPM versions. This is not a claim of a fully locked, byte-for-byte reproducible RPM build. Final image identity, SBOM and dependency inventory remain mandatory.

## Recovery after the recorded deadline

The prior attempt did not complete after the session interruption. `resume-build-result.json` records `ETIMEDOUT` at 2026-10-29T22:31:02.806Z, after the remaining 1,000-second bound. The log reached **module 26/53**, Keycloak JavaScript Parent, during pnpm installation. No compiler failure or final candidate image was recorded. The earlier Maven outputs were inside an unfinished BuildKit RUN layer; inspection after restart found no retained `target` directories or Maven repository. The source/JDK/build-tool layers remained cached. Therefore `-rf` alone could not recover that reactor.

The cached prefix was exported once as a **build toolchain, not a release candidate**:

- Local image/config ID: `sha256:fc62176f4f9a5b2a8b11c16685bfd1f0ac950d710fee0193fe60c00dc211bcfe`.
- Docker export manifest recorded in the log: `sha256:78085a83791cfe9e7d779ee699f2cc0cec3f91cea20e58489b380a6ff654c777`.
- AMD64. The export wrapper reached its 180-second deadline, but the already-running Buildx child completed the export/load; `recovery-toolchain.log`, `toolchain.iid` and subsequent Docker inspection establish completion. This is not reported as a successful wrapper exit. No duplicate export was launched.

On November 1, the existing image was reused to create `workforce-kc-recovery-62b89a2`, ID `b76758be72f827b3eb76d7e385e3747e24a442b47bf0ff01ef3f531b3197bc31`. It is an isolated, persistent experimental container with a 3 GiB memory bound, two CPUs, no production mounts and no published ports. The Maven command uses `install` without `clean`, with a 1,200-second compilation bound. The container is not automatically removed. `/evidence/maven-repository`, `/evidence/maven-home`, `/evidence/home` and `/build/**/target` preserve downloads and outputs for subsequent recovery.

Two bootstrap-only harness failures were diagnosed before compilation:

1. With capabilities dropped, Amazon Linux's `/root` is mode `0550`; Maven could not create `/root/.m2`. Build caches were relocated to `/evidence`, without adding capabilities or changing trust settings.
2. Calling the preserved upstream wrapper under the basename `mvnw.upstream` made it look for `bin/mvn.upstream`. The experiment-only launcher now sources the unchanged upstream wrapper while preserving `$0=mvnw`. Original failure logs are retained in `recovery-first-evidence/` and `recovery-second-evidence/`.

The existing `CheckCerts.java` checker executed against the selected JDK in this container: exit 0, runtime `21.0.13+11-LTS`, **No bad root certificates found**. This checks the default Java trust store for the four roots in the existing checker; it is not an outbound SMTP test or final-image clearance. No Avast CA was imported and no TLS verification was disabled.

## Persistent reactor and Kiota recovery (November 1)

The persistent Maven run completed the first 25 of 53 modules and installed their artifacts into the retained local repository. Node 24.22.1, pnpm 11.1.1 and JavaScript dependencies were installed successfully. Module 26 (`keycloak-js-parent`) failed in documentation generation with TS2307: missing `src/generated/adminClient.js`. No Java/Guava compatibility failure was reported at that point.

Direct execution established that the cached Kiota 1.32.4 native generator required ICU, absent from the build toolchain. Signed Amazon Linux `libicu-67.1-7.amzn2023.0.4.x86_64.rpm` (SHA-256 `3f3e9e76d23311d5c4be74504a543fc406cdbc547b37d43b6b3e005c1b7a7f40`) was verified with `rpmkeys` and its explicitly whitelisted libraries extracted under `/evidence/icu`. These are **build-only** libraries, not an adopted Keycloak runtime change. The RPM, library hashes and signatures remain in ignored experimental evidence. No capabilities were added and no cache was deleted.

With `LD_LIBRARY_PATH=/evidence/icu`, Kiota reported version `1.32.4+c4c336aa144464d5569786de62c3021fd12da755` and generation completed with exit 0. The generated `adminClient.ts` was copied out as evidence. Its output also reported a missing required `protocol` discriminator property in the OpenAPI schema and TypeScript-preview warnings; generation success does not establish that subsequent TypeScript checks pass.

The first `-rf :keycloak-js-parent` resume reused all installations but failed after 33.821 seconds. Investigation found Wireit's earlier OpenAPI task cache had an empty output manifest (`{}`); it restored that output and removed the manually generated client. The Kiota npm wrapper tests only positive exit codes, which can misclassify a signal-terminated native child. The experiment now fingerprints the externally supplied `LD_LIBRARY_PATH` and requires a nonempty generated client after Kiota runs. This preserves the old cache, avoids restoring its invalid success, and strengthens failure detection. Neither TypeScript diagnostics nor compilation checks are disabled. The source-level build-tool change is limited to the ignored experimental admin-client `package.json`; production source/configuration remains unchanged.

The subsequent resume again starts at `keycloak-js-parent`, retaining the preceding 25 modules. Its separate log is `/evidence/resume-icu-maven.log`; earlier failure evidence remains intact. Packaging still uses upstream `-DskipTests` and is not counted as a passing test suite.

## Final candidate evidence

The resumed reactor completed **28/28 remaining modules**, in addition to the 25 previously completed modules: **53 modules built**, not 53 passing tests. The final resume took 16m35s; Quarkus augmentation passed in 80.373s. Neither was repeated for the final OS/permissions correction. Distribution SHA-256: `afdc11a04ef9671ac189c3a059859ab89e81c0689e86325daa949734070e4b40`.

The runtime is **Amazon Corretto 21.0.13.0.11 / 21.0.13+11-LTS, Linux AMD64**, with **Guava 33.7.2-jre** built through source dependency management. Inspection of 486 distribution JARs found one owner of the Guava classes. Its final-image SHA-256 remains `b530942257fb935f8b2cfaa5f8eb5bd59c555fd8e8d01b8ce98912e077ea606c`. Production realm, theme and secret-loading configuration were preserved.

| Identity | Exact value |
| --- | --- |
| Original assembled image/config ID | `sha256:cf57190a035bdffd11440d5b434647c977ec6929e9f4f554bc04266a8bfa43cf` |
| Corrected local image/config ID | `sha256:ab5a6183a7bde972736ba8bd143954e83a641c76158f018fb97c907b0f236f3d` |
| Corrected scan OCI manifest digest | `sha256:b59d35c4330786ad173de31bf98bfcbda6f3d3515d8b4743d6b8accde74de901` |
| Local tag | `workforce-keycloak:corrected-62b89a2` |
| Corrective Dockerfile SHA-256 | `d99ba27c13eaa572a8993c707fda7615ed5cc701428d8735c35c7ef6fdecbc85` |

These are **local experimental identities**, not a published registry digest or signed release provenance. The OCI representation has independently verified layer hashes and the same root filesystem diff IDs and runtime configuration as the Docker image. OCI/Docker config serialization differs; their digests are not presented as interchangeable.

### Runtime permissions: fixed

The original candidate's realm `COPY` created root-owned `/opt/keycloak/data` parents. UID 1000 could not create gzip cache or transaction-recovery files. The correction owns only the data tree as `1000:1000`, creates `tmp` and `transaction-logs`, and sets those directories to `0750`. Runtime UID remains 1000, capabilities/configuration are unchanged, and `/etc/passwd` remains unwritable. The corrected container is healthy, its PostgreSQL container was preserved, and inspection found no gzip-cache creation, permission-denied or recovery-module creation warnings. The production Compose filesystem policy was retained; a wholly read-only Keycloak root filesystem is not claimed.

### Security results and actual exposure

The original full-image scan reported **0 CRITICAL, 16 HIGH, 7 MEDIUM, 1 LOW, 0 UNKNOWN**. The 16 HIGH package records represented ten CVEs. All eight affected packages had straightforward signed Amazon Linux updates; these were applied in a 42.5 MB runtime layer, without another custom Java/Keycloak build or vulnerability exception.

| Original HIGH finding(s) | Affected package(s) and installed correction | Exposure assessment before correction |
| --- | --- | --- |
| CVE-2026-92392 | curl-minimal, libcurl-minimal → `8.21.0-5.amzn2023.0.3` | Malicious HTTP/2 upload responses to curl. Keycloak serves HTTP in Java; this is not evidence of a curl server exposed at ingress. Outbound/native tool use still merits the supported fix. |
| CVE-2026-111497 | libbrotli → `1.1.0-1.amzn2023.0.2` | Crafted compressed data can corrupt decoder memory. Native decoder reachability was not assumed absent; patched rather than waived. |
| CVE-2026-78659, CVE-2026-78667, CVE-2026-94440 | libcap → `2.73-1.amzn2023.0.8` | Scanner associates Go HTTP parsing findings with this RPM. A package match alone does not prove Keycloak's Java ingress invokes those Go handlers. No NOT_AFFECTED determination was fabricated; signed package update applied. |
| CVE-2026-103111, CVE-2026-86145, CVE-2026-89156, CVE-2026-89160 | pcre2, pcre2-syntax → `10.40-1.amzn2023.0.5` | Crafted patterns/subjects require native PCRE2 matching paths; ordinary Java regex is not that engine. Complete non-reachability was not asserted; packages updated. |
| CVE-2026-19553 | python3, python3-libs → `3.9.25-1.amzn2023.0.11` | Python `SSLContext.wrap_bio` without a hostname; Keycloak's tested outbound TLS uses JSSE. OS tooling remains relevant, so patched instead of declaring the package harmless. |

DNF retained `gpgcheck=1` and the official Amazon Linux repository/key. Installed RPM headers record RSA/SHA512 signatures with key ID `e951904ad832c631`; exact versions and signatures are retained. No packages were removed to hide findings. Both scans inventory 130 OS package records and 518 Java package records.

**Corrected full-image scan, 2 November 2026 00:00:26 Europe/Athens: 0 CRITICAL, 0 HIGH, 7 MEDIUM, 1 LOW, 0 UNKNOWN.** Trivy 0.76.0 scanned all severities, with no ignore file or package exclusion. Neither CVE-2026-91415 nor CVE-2026-102554 appears. Guava is detected at 33.7.2-jre. The archive-installed JDK is not RPM-managed: its Java2D remediation evidence is the verified vendor archive/changelog plus effective runtime/module inventory, **not merely scanner absence**.

Retained findings, without automatic approval:

| Severity | Finding(s) | Installed component → scanner-listed fix |
| --- | --- | --- |
| MEDIUM | CVE-2026-113470; CVE-2026-114406 | ca-certificates `2025.2.76-1.0.amzn2023.0.3` → `.0.4` / `.0.5` |
| MEDIUM | CVE-2026-76781 | libxml2 `2.10.4-1.amzn2023.0.21` → `.0.22` |
| MEDIUM | CVE-2026-118259 | opentelemetry-api `1.62.0` → `1.67.0` |
| MEDIUM | CVE-2026-119210, CVE-2026-119276, CVE-2026-119281 | mariadb-java-client `3.5.10` → `3.5.11` (other fixed maintenance branches also listed) |
| LOW | CVE-2026-119280 | mariadb-java-client `3.5.10` → `3.5.11` |

These require the existing baseline/exposure review, not cosmetic library replacement. PostgreSQL is the configured database; the MariaDB driver is bundled but not the selected database path. Caddy strips incoming `traceparent`/`tracestate`; that control is not an exhaustive proof about all telemetry inputs. Native CA packages are separate from the verified Corretto trust store. Nothing in this report signs or approves these findings.

### Scan completeness, failures and freshness

Raw old/new scans, CycloneDX SBOMs, Docker inspection, RPM signatures, Java/module/trust-store hashes and input provenance remain under ignored `.verification/keycloak-bounded-20261029/`, with final results in `corrected/`. The security database was updated **1 November 13:02:34 UTC**, downloaded 16:37:22 UTC, and remains within its stated update interval at the final scan. The Java package-identification index remains from **27 October**: a 947 MiB refresh exceeded its earlier bounded download. This limitation is retained; the separate vulnerability database was current, and effective Java/Guava versions were independently checked. Release automation must apply its normal database-freshness requirements.

Corrected raw scan SHA-256: `5b091a70d44656ae896a523265840e83d654708fe0512bd334904b9fa2ac2777`. Corrected raw CycloneDX SBOM SHA-256: `b15eb6be2a71263835dd2ba0eed40c6cb427c964e12e181cb045085d128d5b71`. Raw evidence is retained, not edited into a clean result.

A Windows Docker archive export timed out at 300 seconds. A direct-daemon scan also timed out retrieving a layer; neither is counted as a clean scan. Recovery reused the previously verified parent OCI layers and only complete new blobs from the interrupted archive. Every selected blob was SHA-256 checked, filesystem paths were never extracted, and the final rootfs/config were matched to Docker inspection. The recovered **full-image** scan and SBOM generation both exited 0. The truncated archive was never accepted wholesale or silently treated as valid provenance.

### Focused runtime and authentication verification

| Check | Result / scope |
| --- | --- |
| Startup, PostgreSQL, realm import, readiness | PASS on corrected image. Existing experimental realm preserved; original application databases untouched. |
| UID/data/cache/recovery permissions | PASS; original defect corrected and startup logs inspected. |
| Effective Java, Guava, trust-store checker | PASS on corrected image; no bad roots found by the existing targeted checker. |
| Outbound HTTPS and wrong-hostname rejection | PASS on corrected image with normal Java certificate validation. |
| Local proxy TLS chain/hostname/HTTPS issuer | PASS with an explicit disposable Caddy CA. This is not a public-domain certificate test. |
| Registration/email verification/OIDC/login/logout/recovery/TOTP enrollment/PNG | **PASS on corrected image**, in the single final paced browser execution. Exact Mailpit recipients, PKCE, signatures, issuer, audience, nonce and verified-email state checked. |
| Fresh-interval TOTP login/logout | **PASS on corrected image**, with a new time-interval code. Replay prevention unchanged. |
| Production SMTP TLS/delivery | **UNVERIFIED.** Local probes encounter the documented Avast TLS interception; no Avast CA was added and certificate checks were not disabled. A clean Contabo-side TLS and delivery check is mandatory. |

The focused harness initially assumed password reset always precedes authenticator setup. Existing Keycloak reset behavior can require `CONFIGURE_TOTP` first and retain older OTP credentials. The active `keycloak.v2` theme selects credentials with a clickable card, not the radio control in the base theme. The corrected test follows actual required actions, selects the new credential, and waits for a genuinely new 30-second TOTP interval. It does not alter authentication policy or allow replay.

The first corrected-image discovery request timed out; a cold class-loading blocked-thread warning was recorded. Subsequent internal discovery and validated TLS probing succeeded. Later registration tests encountered **HTTP 429** after repeated synthetic workflows exhausted the unchanged proxy's 12/minute and 60/hour account-action quotas. These are recorded failures, not successful auth tests. The final harness waits for the established hour window to expire naturally and paces account actions below ten/minute. It does not reset the guard, rotate/spoof client addresses, retry rejected mutations, or increase browser timeouts.

**Final browser result:** completed at **2 November 2026 00:15:01 Europe/Athens** (`2026-11-01T22:15:01.132Z`). **8/8 checkpoints passed in one execution**, no denied/429 responses and no browser JavaScript errors. These are eight checkpoints in one focused authentication workflow, not eight independent E2E scenarios or a rerun of the Workforce suite. Evidence: `runtime/corrected-auth-paced-result.json`; script: `corrected/auth-paced.mjs`. The prior failing attempts remain retained and disclosed above.

**Technical recommendation: VERIFIED.** CVE-2026-91415 is addressed by the verified patched Corretto runtime/vendor evidence; CVE-2026-102554 by the source-built, inspected Guava 33.7.2-jre dependency. The permissions defect and original 16 HIGH OS records are technically corrected. No unresolved HIGH/CRITICAL/UNKNOWN finding appears in this image's completed scan. This conclusion does not approve residual findings, provide vendor support for the custom combination, or authorize production publication/deployment.

Focused commands/evidence used in this continuation (all under `.verification/keycloak-bounded-20261029/`):

- `node corrected/build-desktop.mjs`: minimal OS/data layer build, exit 0. An earlier wrong-builder preflight failed before building and remains recorded.
- `node corrected/start.mjs` and `node corrected/inspect-runtime.mjs`: corrected image healthy; UID, data permissions, Guava hash and warning checks passed; experimental PostgreSQL container unchanged.
- `node corrected/runtime-checks.mjs`: **4/4** Java/version, targeted trust-store, outbound HTTPS and expected wrong-hostname rejection checks passed.
- `python corrected/recover-layout.py` and `node corrected/scan-recovered.mjs`: full-image blob verification, scan and SBOM completed; failed transfer/direct-scan attempts retained.
- `node corrected/proxy-tls.mjs`: HTTPS chain, hostname and issuer passed with the explicit local test CA. Windows Schannel's inability to check revocation for that local CA is not claimed as a production certificate failure.
- `node corrected/auth-paced.mjs`: final **8/8** focused browser checkpoints, no test retries or guard reset.
- `git diff --check`, report UTF-8/whitespace checks, Python syntax and focused Node syntax checks passed. Node syntax checks required outside-sandbox execution after an EPERM path-resolution failure. Docker/browser/network verification also required outside-sandbox execution.

### Minimum remaining release work

1. Review and adopt a reproducible production build for this operator-maintained source/JDK/package combination; production Dockerfiles/Compose remain unchanged in this experiment. Preserve source patches, exact package versions, evidence and maintenance ownership. No additional Keycloak source rebuild is needed merely to finish this experiment.
2. On the final adopted revision, perform the existing exact-revision CI and seven-image release verification with required database freshness, registry/OCI identities, SBOMs and publication provenance. Assess the retained MEDIUM/LOW findings under the existing policy and retain the explicit archive-JDK inventory. Local unsigned evidence is not a release signature; no vulnerability approval was generated here.
3. Before public registration: verify real-domain TLS, clean-host Resend TLS and delivery, production secrets, offsite backup/restore and the existing host-dependent launch checklist. None is implied by this isolated candidate test.

Only experimental artifacts and this report were written. No production configuration, policy, original application container/database/volume, cloud resource or DNS setting was changed. Nothing was committed, pushed, published or deployed by this continuation.
