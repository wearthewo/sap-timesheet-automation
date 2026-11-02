# Final image verification — 2 November 2026

**Technical correction complete. Publication and public deployment remain NO-GO until exact-revision GitHub verification, fresh candidate evidence, configured trust and explicit owner signing. No finding is approved by this report.**

## Narrow backend correction

The seven-image scan of `1f7d8771fa5c7796b13a40d1eab28ce16b053a82` identified backend **CVE-2026-111497** in `libbrotli1 1.0.9-2build6`. Ubuntu's [CVE record](https://ubuntu.com/security/CVE-2026-111497) and [USN-8956-1](https://ubuntu.com/security/notices/USN-8956-1) specify Jammy's supported fix, **1.0.9-2ubuntu0.1**.

`infra/production/backend.Dockerfile` installs that exact version through normal signed Ubuntu APT metadata, with `--only-upgrade` and no extra recommended packages, then asserts the installed version. No base digest, JDK, application code, dependency or release policy changed. Package inventories prove that **only libbrotli1 changed**. The application JAR and secret-loading wrapper have identical before/after SHA-256 hashes.

Patched local candidate:

- Image ID: `sha256:7afbdfadb69c04d436c0f8ac8ed47d6e2898d690afafcf00fd24ff04bdd5ec5c`.
- OCI manifest: `sha256:962af2d816c26b3f65d9e454dd2ceb750d6701cfaff2970cd337a46bf728340b`.
- Source provenance explicitly records base commit `1f7d877…` plus the Dockerfile patch hash and a `-dirty` revision label. It is a compatibility candidate, **not a final publishable source revision**.
- Linux Trivy 0.76.0 scan at **2026-11-02T16:33:11Z**: **0 CRITICAL / 0 HIGH / 0 UNKNOWN / 27 MEDIUM / 16 LOW**; CVE-2026-111497 absent. Advisory database updated 2026-11-02T13:15:56Z; Java index updated 2026-11-02T01:21:00Z. Complete raw scan, SBOM, OCI archive, build metadata and command logs remain under `.verification/backend-brotli-20261102/`.
- Focused runtime checks passed: UID 1000, native FreeType/Brotli linkage, headless font metrics/text rendering/PNG, Java trust-store checker, outbound HTTPS and rejection of a wrong-hostname certificate. Ten verification commands include inventory/hash/compilation prerequisites; these are not ten business browser scenarios.
- `node --test infra/production/production.test.mjs`: **19/19 passed**, zero failures/skips. `git diff --check` passed. Maven packaging reused cached artifacts; no full backend/E2E suite was repeated.

## Seven-image baseline and retained findings

All seven AMD64 images at `1f7d877…` were built with retained provenance and scanned with full package inventories and CycloneDX SBOMs. The table reports those results, substituting the explicitly identified backend follow-up result; **it is not a mixed-revision release manifest**.

| Component | CRITICAL | HIGH | UNKNOWN | MEDIUM | LOW |
|---|---:|---:|---:|---:|---:|
| Keycloak | 0 | 0 | 0 | 7 | 1 |
| Backend patched candidate | 0 | 0 | 0 | 27 | 16 |
| Frontend | 0 | 0 | 0 | 24 | 8 |
| Mock SAP | 0 | 0 | 0 | 24 | 8 |
| Proxy | 0 | 1 | 1 | 4 | 0 |
| PostgreSQL | 0 | 0 | 0 | 1 | 0 |
| Backup | 0 | 0 | 0 | 1 | 0 |

The original backend scan with its HIGH finding is preserved, not overwritten. LOW/MEDIUM findings require the existing enumerated, evidence-backed baseline review; severity does not establish absence of impact.

The original Windows scanner reports have `+02:00` timestamps, while the existing policy deliberately requires UTC `Z`; their advisory database also aged past its 24-hour gate. Those bytes were not normalized or altered. They are diagnostic evidence, not an accepted publication bundle. Final candidate collection must use the existing Linux scanner and fresh metadata. The Linux backend follow-up already demonstrates this collection path. No policy relaxation was introduced.

## Proxy determinations prepared, not approved

Final proxy baseline manifest: `sha256:77833ec729f92c85e3d7dd44d706fd29faf1da58cf0edeada2b63f4ce3ba9873`; executed local ID: `sha256:6989647140c2a1cde95e7790416d9228628ed49c2c9f0eb362e8611047baf520`.

Binary SHA-256 is `8011e71ba2d0a5b77ac986f8b45f147f10105b8f17674b5445b063bb9d4fe42a`, identical to the previously source-reviewed proxy. Its compiled dependency list and effective adapted production configuration also match. Five retained source-evidence hashes were checked against the prior review inventory. The old dependency capture has one extra trailing newline; package paths and order are identical, and both raw captures remain available.

| Finding | Proposed owner determination | Affirmative evidence and limit |
|---|---|---|
| GO-2026-5932, `golang.org/x/crypto v0.58.0`, UNKNOWN | **NOT_AFFECTED — UNSIGNED** | Affected `openpgp` packages are absent from this exact binary's compiled dependency graph. The module-level scanner finding remains visible. |
| CVE-2026-116587, `github.com/go-jose/go-jose/v3 v3.0.5`, HIGH | **NOT_AFFECTED — UNSIGNED, configuration-specific** | Vulnerable library remains present. The exact production configuration has no ACME-server handler, PKI app, internal issuer, on-demand issuance or admin API. Public issuance follows the already traced ACME-client path. Same binary and effective configuration as the retained source review. |

Read [the source-path assessment](phase13-input-path-assessment.md). Unsigned final-image evidence is in `.verification/release-1f7d877/proxy-owner-review/`. A new binary, JWK-consuming plugin/handler, ACME server, internal issuer, admin API or changed effective configuration invalidates the determination. The production host must match the reviewed configuration; synthetic public hostnames in local adaptation are not proof of a real deployment's settings.

There is no third Keycloak exception to manufacture: the corrected runtime no longer reports the earlier Java2D, Guava or trust-store HIGH findings. Retain the runtime remediation evidence instead of approving an absent finding.

## Verification, storage and provenance limits

- [Production Keycloak adoption](phase13-keycloak-adoption.md): 8/8 actual browser checkpoints, four Java/TLS checks and public-certificate chain/hostname validation passed against the production image.
- Build metadata, rootfs-to-OCI bindings, SHA-256-verified archives, unfiltered scans and SBOMs exist for every baseline image. BuildKit reports `materials: false` in completeness: pinned roots/source checksums and recorded dependencies do not constitute signed, exhaustive supply-chain attestation or byte-for-byte Maven reproducibility.
- Keycloak compilation completed under 3 GiB/2 CPU limits in approximately 35m41s active elapsed time after documented host sleeps; 53 packaging modules succeeded, with upstream tests skipped. GitHub's 40-minute compilation/90-minute candidate-job budgets remain unverified on an uninterrupted hosted runner. Do not claim cached local export time as clean-build time.
- Read-only accounting found about 21.06 GiB in `.verification`, including 3.02 GiB in scanner databases, older image copies, a 7.99 GB Keycloak BuildKit cache and 4.66 GB desktop build cache. Docker reports shared layers separately; these totals must not be added as unique physical usage.
- Only three explicitly approved, equivalent Docker-format archives were removed (2,291,895,296 bytes / 2.13 GiB). Retained OCI copies passed all blob hashes and matched filesystem/runtime settings. No image, database, volume or build cache was pruned. Sequential exports completed all five remaining images without another drive. Exact paths and comparison proof remain in `approved-archive-cleanup.json` and `archive-redundancy.json`.

## Minimum remaining release sequence

1. Review/commit this narrow source correction and documentation. With separate authorization, push the final revision and obtain successful exact-SHA CI (`backend`, `frontend`, `mock-sap`, `quality`) and Full-stack E2E (`business-workflows`, including Mailpit). Earlier green runs cannot attest a new commit. No GitHub run was found for `1f7d877…` during this check.
2. Configure the existing `production-publish` and `production` GitHub environments, restricted to main, with matching external `PUBLIC_PRODUCTION_TRUST_JSON`: schema 2, environment `public-production`, approval mode `solo-maintainer`, the owner's Ed25519 public key/ID and revocations. The environment-list API returned no configured environments during this check. No key or setting was generated automatically.
3. After separately authorized merge, run `private-demo-candidate.yml` on exact main with `policy: public-saas`. Its name is historical; the policy selection and protected environment determine public review. Collect fresh UTC scans, exact revision labels, all seven OCI manifests, SBOMs and provenance. Reuse compiled layers, but never relabel old evidence as testing a new manifest.
4. Owner reviews the two proxy technical determinations, every enumerated LOW/MEDIUM baseline and six required control-evidence sections. Rebind all decisions to the final GitHub-built manifest digests and fresh bundle hash. Set explicit remediation/reassessment deadlines and expiry (maximum 30 days for technical assessments; scan freshness remains 24 hours). Solo mode permits no `RISK_ACCEPTED` waiver. Sign exact assessment bytes outside Actions; no signature is supplied here.
5. Supply the non-secret signed assessment/evidence transport at an immutable GitHub commit URL with its SHA-256. Only after separate authorization dispatch `production-publish.yml`, specifying candidate run, assessment URL/hash and `PUBLISH PUBLIC PRODUCTION`. It publishes the reviewed OCI bytes with preserved digests, not rebuilt images.
6. Provisioning/deployment remains separately authorized. Install the identical trust object in root-owned `/etc/workforce/release-policy.json`, mode `public-saas`, plus reviewed evidence on the host. Publication, deployment workflow and host call the same public-policy verifier; live trust parity cannot be proven before a production host exists.
7. Complete actual DNS/TLS, production secrets, clean-host Resend SMTP verification, B2 encrypted upload/restore, firewall, monitoring/expiry response and production smoke checks. Local Avast-intercepted SMTP testing is not production SMTP clearance.

**GO for owner review and separately authorized exact-revision GitHub verification once the source checkpoint is committed. NO-GO for image publication, public registration or deployment until the remaining gates and host checks pass.**
