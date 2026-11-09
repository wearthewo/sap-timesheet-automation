# Public production readiness

This checklist describes the repository's release and operational gates. It does not certify a public deployment or replace an exact-image security assessment. Provider selection, purchase plans and dated launch targets are intentionally outside this engineering reference.

## Repository preparation

- The six-service production topology exposes only Caddy on ports 80/443; application, identity management, PostgreSQL and Mock SAP traffic remains private. See [topology and resource limits](production-deployment.md).
- Public registration uses Keycloak verified identity, BFF session/CSRF protections, backend membership authorization and tenant ownership constraints. The bounded ingress guard and authenticated subject limiter provide abuse controls; they are not upstream DDoS protection. Replicas do not share process-local quota state.
- Production templates include file-secret loading, SMTP STARTTLS, encrypted offsite backup transport, retention templates, restore guards and external heartbeat monitoring. Preparation is not proof of service delivery.
- Publication and host deployment enforce the [same signed public assessment](public-production-release-policy.md). External trust selects independent or explicitly configured solo-maintainer mode. Solo mode accepts only permitted technical determinations and enumerated LOW/MEDIUM baselines, never residual-risk waivers. Private-demo approvals cannot authorize public deployment.
- [Keycloak adoption](phase13-keycloak-adoption.md) and [final-image verification](phase13-final-image-verification.md) retain actual package, authentication and scan evidence. Their historical versions/counts must not be treated as fresh clearance for a later candidate.

## Required for each release

1. Verify CI and full E2E/Mailpit on the exact source revision; retain failures and provenance limitations.
2. Build the seven final AMD64 images and retain manifest digests, unfiltered scans, complete package inventories, SBOMs and provenance. Enforce scanner database and evidence freshness.
3. Review every finding under the configured public policy. Missing, unassessed, expired, tampered or digest-mismatched evidence must fail closed. Keep original findings visible even when a technical determination is signed.
4. Configure matching external trust in the protected `production-publish` and `production` environments and the root-owned host policy. Candidate artifacts cannot supply trusted keys. Keep signing private keys outside the repository and automation artifacts.
5. Publish only the reviewed OCI bytes after explicit authorization. Deploy only the complete immutable manifest from a successful approved publishing run. Revalidate policy at deployment and rollback.

## Requires actual host/service acceptance

- Verify Ubuntu host security, SSH keys and independently checked fingerprints, operator-only SSH, public HTTPS and IPv4/IPv6 firewall rules. Docker-published ports can bypass ordinary UFW forwarding rules; test externally.
- Configure application and identity DNS records, valid public TLS/renewal, exact issuer/callback/logout URLs, secure cookies and protected administration endpoints.
- Generate independent secrets and fresh production data; never import QA identities. A populated legacy migration requires an ownership audit, encrypted backup and disposable restore rehearsal.
- Configure the implemented Resend SMTP integration with a verified sender/domain and SPF/DKIM/DMARC. Verify STARTTLS, hostname validation, registration, recovery, invitations and notifications through the real runtimes. Review shared provider quotas and abuse response.
- Configure a private B2 bucket and narrowly scoped key, encrypted upload/read-back, lifecycle/retention and capacity alerts. Restore an offsite copy on an isolated host; keep age recovery keys independently recoverable. Local retention is not offsite recovery proof.
- Exercise alert delivery, missed heartbeats, backup expiry, reboot recovery, business workflows, tenant isolation and rollback. Measure peak resource use and restore timing; do not infer user capacity from idle samples.
- Establish privacy, retention, contact and account-abuse procedures before admitting public users.

Follow the [provider-neutral operator runbook](deployment-runbook.md), [backup/restore procedure](backup-restore.md) and [operations reference](operations.md). No live-host check is marked passed by this checklist.

### SMTP verification limitation

The historical local SMTP probe encountered workstation TLS interception: issuer `Avast Web/Mail Shield Root`, verification code 19. This is neither proof of a Resend/Keycloak certificate defect nor successful production delivery. Repeat chain/hostname and actual Java-runtime delivery checks on the clean host. Never add the workstation CA to production or disable certificate verification.

## Historical implementation verification — 28 October 2026

Existing application CI/E2E at e500eb3 is preserved and was not repeated for these infrastructure changes. Raw local evidence remains ignored, not committed. No real-host or service-account result is presumed passed.

Completed focused verification:

- Node 24.22.1: `node --test infra/production/release.test.mjs infra/production/production.test.mjs infra/production/monitor.test.mjs infra/production/private-demo-policy.test.mjs infra/production/keycloak-theme.test.mjs infra/production/line-endings.test.mjs`: **90/90 passed**, zero failures/skips. This includes public UNKNOWN rejection, independent private approvals, preflight, monitoring, theme and LF checks.
- Initial rerun after the dependency patch failed one pin assertion expecting x/net 0.60.0; updated the assertion to the actual patched 0.62.0 and added the exact Go JOSE 4.1.6 assertion, then ran the complete focused set above. No assertion was removed. Earlier template change also required explicit synthetic RELEASE_TAG values in valid test fixtures.
- `bash -n` for every production shell script; B2 transport **8**, existing SSH transport **5**, restore guards **4** checks passed in a network-disabled, read-only disposable backup container. These are **17 synthetic checks**, not real B2 transfers/restores. Executable tmpfs was used only by the stub test container.
- `actionlint` and `git diff --check` passed. Production `docker compose --env-file infra/production/production.env.example -f infra/production/compose.yaml config --quiet` passed; this does not validate real secrets/provider access.
- Staged Gitleaks review found one `generic-api-key` match: `b2-rclone.conf.example:9`, the literal `REPLACE_WITH_B2_APPLICATION_KEY` placeholder, not a credential. No suppression was added. Raw scans, generated artifacts, secrets and the two earlier untracked audit reports are excluded from this checkpoint. Staged whitespace review removed one extra template EOF blank line; LF checkout checks passed again after staging.
- Keycloak mitigation image: AMD64/non-root, Java default trust manager and vendor certificate checker passed; Keycloak version remains 26.8.1. Full realm/authentication regression was not repeated for this trust-only change. Local SMTP probes are environment-blocked by Avast as recorded above.
- All **17 pre-existing containers remained healthy**; original databases, realms and volumes were untouched. Fixtures use isolated temporary containers and clean up only their own resources.

Docker builds and isolated runtime checks required outside-sandbox execution. No global Node/Java configuration was changed. No push, publication, purchase, DNS change or deployment occurred.

### Scan evidence (28 October)

Trivy 0.76.0; general DB updated `2026-10-28T01:21:08.391884645Z`, Java DB updated `2026-10-27T01:08:30.264956307Z` (next update October 30). Full OS/language package inventories, unfiltered findings and CycloneDX SBOMs retained. OCI archives were validated before scanning. An initial Windows absolute-path input failed OCI layout resolution; using the same validated layout via a relative path succeeded. That tooling failure was not treated as a clean scan.

Keycloak final scan at `2026-10-28T09:46:44.0877853+02:00`: **0 CRITICAL, 3 HIGH, 0 UNKNOWN, 55 MEDIUM, 35 LOW**. Report SHA256 `c63a133a2158377aee39ae8a82bccde5743838017e35c99902abe966d72421eb`; SBOM SHA256 `da16f71a6da1cfc5a52e5b1ca251aa6672593166a253fa2e16cc978409ba0ee9`. Findings:

| Finding | Installed package | Remediation / runtime assessment |
|---|---|---|
| CVE-2026-91403 HIGH | java-21-openjdk-headless 1:21.0.12.1.1-1.2.el9 | Effective Java CA mitigation verified, RPM finding remains; independent vendor-backed classification required. |
| CVE-2026-91415 HIGH | same | Font-rendering issue; no exposed graphics-input path demonstrated, but no supported patch or complete non-applicability proof verified. |
| CVE-2026-102554 HIGH | com.google.guava:guava 33.6.0-jre | Excessive allocation on Java collection deserialization. Fix 33.7.2 exists; a reachable untrusted Java-deserialization path was not established. Supported integration/update still required. |

These are **local candidate artifacts**, not approved registry manifests or signed release provenance. The other five images were unchanged and not rebuilt/rescanned here; their historical clean scans cannot establish clearance against the refreshed database. The final seven-image publication gate must run on the authorized final revision.

### Final proxy patch and remaining clearance

Patched Go JOSE **v4.1.6** and x/net **v0.62.0**, with required compatible x/crypto 0.58.0, x/sync 0.24.0, x/sys 0.49.0, x/term 0.47.0 and x/text 0.43.0. Go module checksums verified; no replacement fork, package exclusion or disabled checksum verification. Final build passed all **7 ingress unit tests**. Production-constraint runtime fixture `workforce-runtime-check-3dc2357f2ad4` passed **7/7 checks**: health, TLS/redirect/forwarding, auth route/body boundary, real read-only QA login/health/discovery, HTTP/2, registration throttling and graceful shutdown. Only fixture containers/network were removed.

Final proxy scan `2026-10-28T14:22:19.3950248+02:00`: **0 CRITICAL, 1 HIGH, 1 UNKNOWN, 2 MEDIUM, 0 LOW** (32 OS and 162 Go packages inventoried). This removes the v4 Go JOSE occurrence and both x/net UNKNOWN occurrences. Remaining:

- **CVE-2026-116587 HIGH**, go-jose/v3 **v3.0.5**, no fixed v3 version in the advisory. Crafted RSA JWK parsing can exhaust CPU. The production proxy does not expose its admin API or configure client-supplied JWK parsing, but those controls do not prove all transitive call paths safe. Requires a supported dependency migration/backport with compatibility verification; not waived.
- **GO-2026-5932 UNKNOWN**, x/crypto **v0.58.0**. The final build verifies that no affected OpenPGP package is imported and records the package list. The containing module remains detectable; independent not-affected review/scanner precision is unresolved. Strict gate still rejects it.

| Final local candidate | Docker local configuration ID | Validated OCI manifest digest |
|---|---|---|
| Proxy | `sha256:43edc0f8d6b440194692399cdc04b8e27fe66f1d5171c7cc28d7be138bfb35db` | `sha256:f046885d046218b888f969b0c632f1d0695883b9e5def3bf32df00c539ba5c61` |
| Keycloak | `sha256:64fc6a7800f64594166fa97a6213af40d2abd557231aa32ef1eae2415cc79d22` | `sha256:a117385d0366500e088293cf03c2275d03a05b708ed91286a1e825b4195b3637` |

Proxy report SHA256 `e07d04949e0b3386fde31f18d5f83a169a0da1d804ad5863e8dda2479a7b1d8f`; SBOM SHA256 `8d0fb4aea5ca9bb3b1ec40c9ef56fbeaeb9164526dc05781295294786e9e891c`. Evidence roots: `.verification/public-ready-20261028/` and `.verification/public-patch-20261028/`. Build context file hashes and parent revision `4ede5f578a917d487e770a2b29ac147038a45d51` are retained locally. These builds precede the checkpoint commit and are explicitly labelled uncommitted candidates, not exact-revision publication provenance.

Direct policy evaluation of both final scan JSON files correctly rejected them (**2/2 expected rejection checks**). The full scan output remains intact; no exception, altered severity or package exclusion was used.

These results describe the October 28 candidate only. Later [Keycloak adoption](phase13-keycloak-adoption.md), [input-path analysis](phase13-input-path-assessment.md) and [final-image verification](phase13-final-image-verification.md) record subsequent work. Historical clean scans and unsigned determinations do not authorize a new release. Evaluate the exact current candidate under the existing release policy.

## Primary evidence

- [Go advisory](https://pkg.go.dev/vuln/GO-2026-5932)
- [Red Hat certificate finding](https://access.redhat.com/security/cve/cve-2026-91403), [Java2D finding](https://access.redhat.com/security/cve/cve-2026-91415)
- [Go JOSE advisory](https://github.com/go-jose/go-jose/security/advisories/GHSA-mgcc-cww5-fp2q), [Guava advisory](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94), [latest official Keycloak](https://github.com/keycloak/keycloak/releases/latest)
- [Resend SMTP](https://resend.com/docs/send-with-smtp), [quotas](https://resend.com/docs/knowledge-base/account-quotas-and-limits)
- [B2 S3 API](https://www.backblaze.com/docs/cloud-storage-s3-compatible-api), [lifecycle semantics](https://www.backblaze.com/docs/cloud-storage-lifecycle-rules)
- Existing immutable scan and compatibility evidence: `phase13-container-blockers-resolution.md`.
