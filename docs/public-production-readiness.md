# Public production readiness — 28 October 2026

Target selected by the operator: **Contabo Cloud VPS 6, 12 GB RAM, AMD64 Ubuntu 24.04**, Namecheap DNS, Resend SMTP, encrypted Backblaze B2 backups, Caddy and GitHub Actions/Compose. No service has been purchased or configured. This document supersedes the earlier private-demo target; historical test reports remain evidence, not current deployment instructions.

**Decision: BLOCKED / NO-GO before purchase.** Public registration is the target, but unresolved runtime findings must not be accepted through private-demo exceptions. No images have been published and no data has been migrated.

Latest continuation: [October 29 input-path assessment](phase13-input-path-assessment.md), following the [weekend clearance report](phase13-weekend-clearance.md). Policy committed at `260e1766e9db5b426cef4f1a1e8559bf3606e602`; its historical 143 policy/configuration tests, 7 OCI tests and 2 LF tests were not repeated. New exact-image configuration checks and five benign Java probes passed. OpenPGP and configuration-specific Go JOSE are candidate not-affected determinations; Java certificate-store remediation is verified but unapproved. Java2D and Guava remain blocked on supported fixes or complete independent applicability review. No findings are approved and final seven-image exact-revision evidence is pending. No production image changed in this continuation.

## READY BEFORE PURCHASE

29 October solo-maintainer update: the explicitly authorized schema 2 public approval mode allows the named trusted owner to sign evidence-backed NOT_AFFECTED/REMEDIATED determinations and exact LOW/MEDIUM baselines. It rejects every RISK_ACCEPTED decision. Matching external trust mode and signature are required in GitHub and on the host; candidate artifacts cannot select trust. Existing independent and private-demo behavior is preserved. Focused policy/configuration verification passed 176/176 with zero failures/skips. See [the current operator procedure](public-production-release-policy.md#single-maintainer-setup-and-operation). No current finding has been approved, and Java2D/Guava remain blocked. The paragraph below records the earlier independent-policy checkpoint; solo mode supersedes its mandatory-second-reviewer requirement only when deliberately configured.

Policy implementation update: [the public assessment operator guide](public-production-release-policy.md) documents the authorized signed assessment mechanism, shared publication/host verification and enumerated LOW/MEDIUM baseline. This supersedes earlier descriptions below of an absolute scanner-only publication rule. The raw scanner gate still rejects HIGH/CRITICAL/UNKNOWN; the public assessment route requires independently signed, exact-digest technical determinations or eligible limited risk acceptance. **None of the five current findings has been approved.** Historical scan/test results below are unchanged. Final source CI and fresh seven-image evidence remain pending authorization.

- Existing six-service architecture retained. Only Caddy publishes TCP 80/443; PostgreSQL, backend, frontend, Keycloak management/admin and Mock SAP remain private. TLS, exact issuer/callbacks, confidential PKCE client, verified email, secure BFF sessions, tenant membership authorization and database ownership are preserved.
- Existing memory caps total **5,632 MiB**, plus the operator-only 512 MiB backup process during maintenance. Keep these caps on the 12 GB server; extra RAM is OS/cache/headroom, not a promised user capacity. Runtime process limits are now 512 per shared application container. PostgreSQL keeps its existing connection/memory limits. Do not run builds on the serving VPS.
- Production proxy now includes a small repository-owned ingress guard, compiled with existing pinned dependencies. It uses the TCP peer, never X-Forwarded-For, and groups IPv6 clients by /64. Per host: 600 requests/client/minute, 3,000 globally/minute, at most 64 concurrent upstream requests, 10,000 retained quota keys. Authentication realm requests: 30/client/minute and 600 globally/minute. Login actions/registration: 12/client/minute, 60/client/hour, 300 globally/hour. Application mutations: 30/client/minute and 300 globally/minute. Saturated quotas fail closed with 429, Retry-After and no-store. Existing 128 KB body limit/timeouts remain.
- Authenticated organization/invitation mutations additionally use the existing backend subject limiter, configured to 5/minute in production. This is independent of IP addresses and organization switching.
- Resend defaults: smtp.resend.com, username resend, port 587 with STARTTLS. Actual API key remains an operator-supplied secret file. Both Keycloak and Workforce share provider quotas. No real emails were sent. Local TLS probes are intercepted by Avast, as detailed below; they are not evidence of a Resend certificate defect.
- B2 transport uploads only `.tar.age` plus checksum, refuses insecure configuration, uses HTTPS, immutable copy and bounded transfer time, and **downloads ciphertext to verify SHA256 before recording success**. No decryption key or remote-delete permission is needed on the application host. Existing SSH transport remains available for compatibility.
- Local retention remains 14 days. `b2-lifecycle.json` is an unapplied operator template for hiding objects after 30 days, deletion one day later and abandoned multipart cleanup. Existing coordinated backup, empty-target restore, migration/content manifests, maintenance locks, rollback guards and independent monitoring remain.
- Public publication now explicitly rejects UNKNOWN as well as HIGH/CRITICAL. Deployment rechecks all seven retained public scan reports. Private-demo approvals cannot authorize public deployment. No finding has been suppressed or approved.

### Limits of abuse protection

Counters are bounded and in-memory in the single proxy; restart/config reload resets them. They do not protect the VPS uplink from distributed denial of service, provide durable tenant storage quotas, or guarantee that Resend's daily allowance cannot be exhausted. Shared NAT users share a budget. Forwarding through a CDN would require a separate trusted-proxy review; the current design assumes direct DNS to Caddy. Do not place a CDN in front and blindly trust its forwarding headers.

Resend Free currently limits sending to 100 emails/day and 3,000/month, shared by account verification, recovery, invitations and notifications. Provider suspension, quota exhaustion and bounce/spam limits can prevent onboarding. Configure provider alerts and capacity policy before opening registration. CAPTCHA and durable account/storage quotas remain a decision if the small initial public service encounters abuse; rate limits are not an unlimited public-SaaS capacity claim.

## BLOCKED

1. **GO-2026-5932**: the advisory affects only the unmaintained OpenPGP packages. Existing exact-build evidence showed zero affected imports. New proxy builds retain their dependency list in `/usr/share/workforce/caddy-dependencies.txt` and fail compilation if an affected package appears. The scanner still reports the containing module. Independent review/upstream precision or an explicitly approved evidence-based not-affected classification is needed; no automatic public exception mechanism was added.
2. **Keycloak CVE-2026-91403**: fingerprint-checked removal of the remaining G1 root using keytool passed the vendor certificate checker in the final image; the default Java store contains 117 trusted issuers. The stripped image's `update-ca-trust` failed because libp11-kit is absent, so no claim is made that all OS bundles were regenerated. The Java-store mitigation does not change installed RPM metadata or automatically clear the scanner finding.
3. **Keycloak CVE-2026-91415**: October 28 official metadata still identifies RHEL 9 java-21-openjdk as affected; no fixed release was supplied in that record. Latest official Keycloak remains 26.8.1. No unverified JDK transplant was made. No exposed graphics path has been demonstrated, but bounded source inspection is not complete non-applicability proof. Backend JRE applicability also needs closure despite its scanner coverage limitation.
4. **Final release evidence**: local candidates are not signed/published exact-revision release images. Changed image scans, review of all retained findings, independent environment protection and authorized exact-revision GitHub verification are required before publication. Public release remains blocked, including on a supposedly non-affected UNKNOWN, until that classification is independently resolved.
5. **New October 28 findings:** the refreshed database additionally detects CVE-2026-116587 in Go JOSE v3/v4, CVE-2026-78661 and CVE-2026-97045 in x/net, and CVE-2026-102554 in Keycloak's Guava. These were not present in the earlier reported totals. Go JOSE v4.1.6 and x/net v0.62.0 are available patches; the build below verifies their adoption. Go JOSE v3.0.5 has no fixed v3 release identified by the advisory. Guava has a 33.7.2 patch, but latest official Keycloak 26.8.1 still bundles 33.6.0-jre. No unsupported jar transplant was made. A supported Keycloak update or a separately compatibility-verified source build is required; a quota or absence of a demonstrated exploit does not clear the finding.

Correction to previous reporting: inspection found the old `scan-verification.mjs` public gate rejected HIGH/CRITICAL but accepted UNKNOWN; earlier prose describing it as rejecting UNKNOWN was inaccurate. This checkpoint adds an explicit rejection regression test and deployment revalidation. It strengthens the gate rather than granting clearance.

## REQUIRES REAL HOST OR SERVICE

### SMTP environmental limitation: Avast interception

On October 28, the operator independently tested Resend ports 587 and 465 with OpenSSL. Both connections presented **Avast Web/Mail Shield Root** as issuer, with `self signed certificate in certificate chain` and verification return code **19**. Local Java STARTTLS probes failed certificate validation in both the original and mitigated Keycloak images. Treat these local results as interception by the Windows security software, not proof of a Resend or Keycloak certificate defect, and not a regression caused by the trust-store correction.

No TLS verification was disabled and no Avast CA was added to any production trust store. Before public launch, repeat SMTP TLS/hostname validation from the clean Contabo host using the actual Keycloak/backend runtimes, then deliver controlled verification, recovery, invitation and notification emails. The clean-host TLS and real Resend delivery checks remain **NOT TESTED**. Do not send real credentials through the intercepted local connection.

- Purchase/domain steps remain unauthorized. Confirm actual Contabo invoice, IPv4/IPv6, VAT/renewal and service limits before spending money. No guaranteed monthly total is claimed.
- Configure Namecheap A records for app and identity hosts, no URL forwarding. Configure AAAA only after IPv6 firewall/routing validation. Set Resend-provided DNS records exactly; verify SPF/DKIM and choose a monitored DMARC policy.
- Configure public TCP 80/443, operator-only SSH, MFA and checked SSH host fingerprints. No visitor allowlist or VPN. Inspect external IPv4/IPv6 exposure because Docker publication can bypass ordinary UFW rules.
- Generate independent production secrets and a clean production database/realm. Do not import QA accounts. Supply Resend sending credentials for the verified domain; test required STARTTLS/hostname verification, verification/recovery/invitation mail and delivery limits with consenting test recipients.
- Private B2 bucket, narrowly scoped application key, lifecycle rules, storage/billing alerts, read-back test and an actual offsite restore. A free storage allowance is not unlimited retention: estimate all ciphertext versions and upgrade/pre-deploy backups. Keep age recovery keys offline and separately backed up.
- Independent external heartbeat/alert destination, TLS issuance/renewal, backup/restore timing, reboot recovery, public browser smoke and measured host memory/disk. Local fixtures cannot verify these services.
- Operator privacy/retention/contact policy, account-abuse response and GitHub reviewers with no self-approval. The app contains sensitive employee/time data; opening public registration is an operational commitment.

## Operator sequence once blockers are resolved

1. Review and authorize the final commit/push; obtain exact-revision CI/E2E required by the existing release gate. Authorize public publication only after all seven reports and independent reviewer controls pass.
2. Purchase the selected server/domain only after a separate approval. Follow the host/SSH/Docker instructions in `contabo-deployment.md`; use this document's public network/provider settings.
3. Install root-owned templates outside Git. Configure `release-policy.json` with `mode: public-saas`, external public reviewer trust and matching signed evidence as specified in the public assessment guide. Mode alone does not authorize deployment. Use only the public publication workflow and `public-saas` deployment mode. Install a reviewed immutable release manifest and source checkout.
4. Install Ubuntu's reviewed `rclone` package, record its version, and copy `b2-rclone.conf.example` to `/etc/workforce/offsite/rclone.conf` (root:root, 0600). Fill bucket-scoped credentials and the actual B2 regional HTTPS endpoint. Set `BACKUP_TRANSPORT=b2` and `B2_DESTINATION=workforce-b2:YOUR_BUCKET/workforce` in the root-owned operations file. The destination key requires read/write/list access; no remote-delete access. Apply lifecycle rules separately as the bucket administrator.
5. Configure DNS, SMTP, secrets, offline recovery keys and monitoring. Validate environment and Compose before the separately authorized deployment. Never log resolved secrets.
6. After deployment authorization: test both HTTPS hosts/issuer/callbacks, secure cookies, external port isolation, registration/recovery, tenant separation, controlled email, offsite restore and alerts. Verify rollback compatibility before any upgrade. Open registration only after acceptance.

## Verification record

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

**Final decision: repository corrections verified and suitable for local checkpoint review; public image publication, purchase-for-immediate-launch and deployment remain NO-GO.** Across the two newly scanned images there are **4 HIGH + 1 UNKNOWN**, zero CRITICAL. Do not promise October 30 clearance or purchase on that assumption. Follow the conditional October 31–November 1 sequence in [the Contabo runbook](contabo-deployment.md); postpone if the security gate remains blocked. No private-demo exception is usable for this public target.

Minimum remaining work: resolve the v3 JWK dependency and supported Keycloak Guava/JDK corrections, independently close the OpenPGP/CA classifications, then separately authorize exact-revision GitHub verification and seven-image publication. Real DNS/TLS, clean-host SMTP, B2 restore, alerts and public-port acceptance necessarily follow authorized service provisioning. No repeated application development phase is required.

## Primary evidence

- [Go advisory](https://pkg.go.dev/vuln/GO-2026-5932)
- [Red Hat certificate finding](https://access.redhat.com/security/cve/cve-2026-91403), [Java2D finding](https://access.redhat.com/security/cve/cve-2026-91415)
- [Go JOSE advisory](https://github.com/go-jose/go-jose/security/advisories/GHSA-mgcc-cww5-fp2q), [Guava advisory](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94), [latest official Keycloak](https://github.com/keycloak/keycloak/releases/latest)
- [Resend SMTP](https://resend.com/docs/send-with-smtp), [quotas](https://resend.com/docs/knowledge-base/account-quotas-and-limits)
- [B2 S3 API](https://www.backblaze.com/docs/cloud-storage-s3-compatible-api), [lifecycle semantics](https://www.backblaze.com/docs/cloud-storage-lifecycle-rules)
- Existing immutable scan and compatibility evidence: `phase13-container-blockers-resolution.md`.
