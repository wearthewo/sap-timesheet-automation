# Keycloak production adoption — 2 November 2026

Status: **verification in progress; publication and deployment remain NO-GO**.

The reference experiment is documented in [the bounded remediation report](phase13-keycloak-bounded-remediation.md). Its corrected local Docker image ID is `sha256:ab5a6183a7bde972736ba8bd143954e83a641c76158f018fb97c907b0f236f3d`. That image passed eight browser checkpoints, effective Java/trust-store/HTTPS checks, and a full-image scan with 0 CRITICAL, 0 HIGH, 0 UNKNOWN, 7 MEDIUM and 1 LOW findings. These results belong to that experimental image, not automatically to a new production build.

## Build adopted

`infra/production/keycloak.Dockerfile` now builds Keycloak 26.8.1 from its SHA-256-pinned upstream source archive. It applies the proven source-level Guava 33.7.2-jre/failureaccess 1.0.3 dependency overrides and the Kiota generated-output check. Build-only ICU is installed; the runtime does not gain build tooling. Maven packaging skips upstream tests, explicitly separate from authentication acceptance.

Amazon Corretto 21.0.13.0.11 is pinned by vendor archive checksum. The [vendor release](https://github.com/corretto/corretto-21/releases/tag/21.0.13.0.11) publishes the same Linux x64 checksum. The base image digest, exact runtime RPM updates, immutable Amazon repository snapshot and RPM signature verification are recorded in the Dockerfile and `keycloak/inputs.json`. No experimental local image, prebuilt developer distribution, floating JDK download, JAR overwrite, vulnerability exclusion or TLS bypass is used by the production definition.

The production realm, theme, secret-loading entrypoint, PostgreSQL build option and health configuration remain unchanged. UID/GID 1000 and the verified data-directory ownership correction are preserved. The new runtime uses Corretto's checked trust store; the previous UBI-specific trust-store patch is retained in history/source for evidence, but is not incorrectly applied to Amazon Linux.

This is an **operator-maintained source build**, not an official supported Keycloak binary combination. Inputs are reproducible and checked; byte-for-byte reproducibility of upstream Maven output is not claimed. Maven/npm transitive artifacts and timestamps still require inventories and final-image review. A new upstream release or changed dependency requires renewed compilation, scanning and authentication evidence. The patched runtime is **AMD64 only**; multiarch base-image availability does not establish ARM64 support for this distribution.

## Required evidence before release

1. Successful production source build, distribution integrity guard and augmentation.
2. Effective Java, Guava class-owner/hash and trust-store checks on its exact image; retained realm/theme/wrapper comparisons, UID/data checks and focused authentication acceptance.
3. All seven final AMD64 images from the reviewed revision, with OCI manifest identities, unfiltered scans, SBOMs, scanner database dates and provenance. A local image ID is not a registry manifest digest.
4. Exact-revision GitHub CI/E2E and candidate workflow. No result from the earlier revision is relabelled as testing this change.
5. Explicit owner review/signature under the existing externally configured solo-maintainer trust policy. No approval is produced by building or testing.

The three historical review candidates are proxy GO-2026-5932 (affected OpenPGP packages absent), proxy CVE-2026-116587 (configuration-specific Go JOSE input-path assessment), and Keycloak CVE-2026-91403 (certificate-store remediation). Rebind applicable decisions to **new exact image digests** and preserved source/runtime evidence. If the new scan no longer reports a finding, retain the remediation record but do not fabricate a finding-specific exception: the policy rejects decisions that do not match the bundle.

The seven MEDIUM and one LOW records in the reference Keycloak scan remain visible. The existing signed, enumerated baseline mechanism may be used only after the owner reviews actual exposure/impact and remediation dates. Severity alone is not approval. MariaDB is bundled but PostgreSQL is selected; Corretto's trust store is separate from the OS CA package; ingress strips telemetry headers, which is not proof of exhaustive telemetry unreachability.

## Local checks so far

- Focused production, line-ending, release, public-assessment and theme checks: **136/136 passed**, zero skipped. The first smaller run had a test-parser failure on an existing `FROM --platform=...` directive; the parser was corrected to distinguish pinned roots and previously defined stages, then the complete focused selection passed.
- Actionlint: passed. `git diff --check`: passed (Windows text normalization notices only).
- Six source/dependency-integrity failure tests passed. The preparation script successfully processed the pinned upstream archive in isolation, and the actual retained distribution passed the Guava guard (486 JARs, one class owner, expected SHA-256).
- Production Compose configuration validation passed. Candidate CI now retains full BuildKit metadata separately from the single-manifest OCI archive; its 90-minute job budget includes the new source build's bounded 40-minute compilation. This does not alter test timeouts or security policy.
- Production source compilation: **53/53 reactor modules built**, with upstream tests skipped for packaging. Guava integrity guard and Quarkus augmentation passed (99.289 seconds). The first OCI export completed; final revision-labelled image authentication/scans remain pending. Logs are retained under ignored `.verification/keycloak-adoption-20261102/`.
- Build timing: Maven reported 4h22m wall time. Windows Power-Troubleshooter records confirm three overlapping host sleeps totalling 13,633.934 seconds; subtracting them from the 15,775.1-second compilation step gives approximately **35m41s active elapsed time**. This is not a CPU-time or GitHub performance guarantee. Builder limits were 3 GiB and 2 CPUs; no OOM termination occurred. Candidate CI now uses those same limits, a 40-minute compilation deadline and a 90-minute overall job deadline. An uninterrupted GitHub run is still required; deadlines were not removed to hide failures.
- Java scanner index refresh succeeded through the official GHCR repository in 8m33s. Its UpdatedAt is 2026-11-02T01:21:00Z and NextUpdate is 2026-11-05T01:21:00Z. The previous stale index is not used to claim release freshness.

No application container, database, volume, production credential or policy approval was changed. No push, publication or deployment occurred.

## Host-dependent launch checks

Actual DNS/public certificates, production secrets, clean-host Resend TLS and delivery, B2 encrypted upload/restore and monitoring remain pending until separately authorized infrastructure exists. The local SMTP probe is affected by Avast interception and is neither proof of a provider defect nor production SMTP success. Never import Avast's CA or disable certificate verification to clear that check.
