# Phase 13: private-demo release policy proposal

26 October 2026. **DESIGN FOR REVIEW ONLY. No exception is approved or enabled.**
The existing strict scanner and publication gates are unchanged. This document
does not authorize pushing, publishing, provisioning or deployment.

## Decision and current evidence

An IP-restricted demo can reasonably use a different risk acceptance policy from
an unrestricted SaaS. IP restriction reduces exposure; it does not repair a
vulnerability or protect against a compromised authorized tester. Keep synthetic
business data, authenticated users, organization authorization, TLS, secrets,
network isolation and encrypted recoverable backups under both policies.

The frontend's **actual assembled AMD64 image**, not just its distroless base,
has 0 CRITICAL / 0 HIGH / 23 MEDIUM / 8 LOW findings. See the
[runtime report](phase13-frontend-runtime-remediation.md) for immutable identities,
the complete inventory, scanner date and browser limitations. No waiver is needed
for its old Bookworm HIGH/CRITICAL findings: that runtime was replaced.

The previous Caddy/PostgreSQL/backup remediation remains valid within its recorded
scan date. Those unchanged images were not rebuilt or rescanned in this checkpoint.
The entire seven-image release is **not cleared**.

| Finding / evidence | Private-demo disposition proposed for review |
|---|---|
| Keycloak `java-21-openjdk-headless 1:21.0.12.1.1-1.2.el9`, HIGH CVE-2026-91403 | Potential vendor-mitigated exception, **not approved**. Red Hat says its non-portable RPM consumes the OS CA trust mitigation. Verify the actual JVM trust-store linkage and all implicated root/intermediate fingerprints before relying on that mitigation. Restrict outbound destinations; inbound IP filtering alone does not prevent outbound TLS impersonation. |
| Same JDK, HIGH CVE-2026-91415 | Potential short private-demo exception after exact-image graphics/font input-path review. Vendor describes crafted graphics input to exposed Java 2D APIs. No such Keycloak route was demonstrated here; this is not proof of unreachability. No custom providers/themes accepting untrusted graphics; trusted testers and synthetic data only. A reachable attack path would block release. |
| Frontend 23 MEDIUM / 8 LOW | Does not fail the existing severity threshold. Retain all findings and schedule supported base updates. No assertion that these are harmless; elevate any demonstrated exploitable defect regardless of scanner severity. |
| Mock SAP's existing final-image scan: 1 CRITICAL / 56 HIGH | **Unclosed release scope**, not covered by the frontend replacement. Historical image ID `sha256:b94ff199419c5dd82cd825ea62872e304ae60103a1263a8a5665d24a296764a3`; current Dockerfile still uses the same Bookworm base digest `55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205`. This is existing evidence, not a fresh scan of a new production build. Prefer reusing the proven minimal Node runtime approach for this built-in-only simulator rather than granting 57 blanket waivers. Requires its own secret loader, persistence and HTTP compatibility checks. |
| Mock SAP CRITICAL CVE-2023-45853, `zlib1g 1:1.2.13.dfsg-1` | Debian explicitly qualifies the Bookworm source finding: vulnerable contrib/minizip is not built into the affected source's binary packages. Reasonable candidate for a specific not-affected exception **only after checking the final image for bundled/separate MiniZip consumers**. Does not excuse unrelated HIGH findings. |
| Other Mock SAP HIGHs | Package occurrences are not 56 remotely exploitable Node bugs. They include util-linux family, gzip, ACL, systemd/udev libraries, ncurses and Perl. Process is an internal Node HTTP simulator, not these utilities; actual per-advisory reachability is not fully established. No blanket approval. Exact existing findings are in `docs/phase13-private-demo-pending-findings.csv`. |
| Backend Java 21.0.12.1 runtime | OS-only scan success does not close Java advisory coverage. Assess both JDK advisories against the exact Temurin distribution/trust store and application paths. Red Hat's RPM mitigation must not be copied to Temurin without evidence. Source inspection found POI font creation in reporting/export formatters; no ImageIO, BufferedImage or multipart upload hits in the reviewed source search. That search alone is not a reachability proof. |
| Retained UNKNOWN findings in Caddy/PostgreSQL/backup | Not equivalent to LOW. Existing inventory includes CVE-2026-107891 (musl), GO-2026-5932 (x/crypto), CVE-2026-39824 (x/sys). Review vendor behavior and actual use before demo approval; no new findings were suppressed. MEDIUM zlib and chi findings remain visible too. |
| Keycloak registration JavaScript null listener | Existing UI defect reproduced on unchanged application. Registration/recovery/invitation actions completed, but focused browser zero-error assertion fails. Not established as a security exploit. Track separately as functional acceptance; do not make a vulnerability exception silently waive the browser gate. |

On the disposable unchanged Keycloak runtime, `keytool -list -cacerts` returned
118 entries; the filtered alias result retained `emsignrootca-g1`, with no G3 alias
in that output. `/etc/pki/java/cacerts` pointed to the extracted OS trust store.
This is **partial evidence only**, not a fingerprint-level proof of the complete
vendor mitigation or of the effective JVM store. The attempt to query `rpm` in
that minimal container failed because the utility is absent; the package versions
above come from existing scanner evidence. No trust store was changed.

Official evidence, reviewed 26 October:

- [Red Hat CVE-2026-91403](https://access.redhat.com/security/cve/cve-2026-91403): OS trust-store mitigation for non-portable RPM JDKs; portable builds differ.
- [Red Hat CVE-2026-91415](https://access.redhat.com/security/cve/cve-2026-91415): Java 2D crafted-input vulnerability; Red Hat CVSS 4.8 versus cve.org 7.0, while the recorded scanner severity remains HIGH.
- [Debian CVE-2023-45853](https://security-tracker.debian.org/tracker/CVE-2023-45853): MiniZip qualification for Bookworm; no blanket zlib safety claim.
- [Temurin 21.0.13+9](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.13%2B9): patched binary exists. Existing official image/RPM checks found no updated compatible packaged candidate. A custom JRE replacement is not yet verified.

## Policy 1: unrestricted public SaaS

Keep zero HIGH/CRITICAL findings in every assembled runtime image, with all
severities retained in reports, fresh advisory data, supported dependencies and
complete Java/application coverage. No private-demo exceptions apply. Exact-SHA
CI/E2E, production security controls and business acceptance remain mandatory.
Zero scanner findings are necessary under this policy, not sufficient proof of
security. Unassessed UNKNOWNs or a demonstrated lower-severity exploit can block
release independently. Switching from private to public requires a new review.

## Policy 2: IP-restricted private portfolio demo

Proposed maximum exception duration: **14 days**, no automatic renewal. Every
exception requires a named risk owner and explicit independent approval of that
specific artifact and finding, with a patch/reassessment deadline. Approval of
this design is separate from approval of actual exceptions.

Potentially acceptable: vendor-confirmed non-applicability; verified vendor
mitigation; or a documented inaccessible vulnerable feature in a constrained,
synthetic-data deployment. Not acceptable: demonstrated reachable RCE/auth bypass,
tenant data disclosure, arbitrary untrusted input reaching a memory-safety flaw,
missing image evidence, or an exception whose controls cannot be verified.

Required compensating controls:

- Default-deny IPv4 **and IPv6** allowlists for trusted tester IPs on both app and
  authentication hosts; restrict SSH independently. Test denial from a source
  outside the allowlist and access from a permitted source. Address Docker's
  published-port firewall behavior; UFW alone is not evidence of isolation.
- No public backend, PostgreSQL, Mock SAP, Mailpit, Keycloak admin or management
  ports. Current Compose publishes proxy ports on `0.0.0.0` by default; a runbook
  description does not implement an allowlist. Real host/firewall verification is
  a deployment prerequisite. Do not expose all users while waiting for it.
- Existing Keycloak login, verified email, BFF cookies/CSRF, membership/role and
  tenant checks unchanged. Only known testers; no real customers, production SAP
  or sensitive employee data. Restrict outbound integrations to reviewed endpoints.
- Verified TLS for both hosts; certificate renewal plan compatible with ingress
  restrictions (do not open the application globally for ACME convenience).
- Secret-file protections, least privilege, container isolation, encrypted offsite
  backups with a restore test, monitoring and an operator response plan retained.
- Before expiration, patch and approve a new artifact or close tester ingress.
  Do not automatically delete data. Expiration monitoring must alert in advance;
  publication/deployment gates alone cannot stop an already running expired demo.

## Exact-digest enforcement design: NOT IMPLEMENTED

Inspected `scan-verification.mjs`, `release-gates.mjs`, `release.mjs`, and
`production-publish.yml`. **Current automation cannot approve scoped exceptions.**
It rejects every HIGH/CRITICAL and has no waiver path. Focused tests confirm the
existing strict fail-closed behavior. Expiration/digest/approval enforcement below
is a design, not a tested implemented capability.

The current workflow obtains registry digests after pushing. Its environment
approval happens before images are built. That cannot prove approval of an exact
previously unknown image digest. After design approval, use this bounded change:

1. Build seven AMD64 artifacts once into retained OCI artifacts without publishing.
   Calculate their actual manifest digests, config IDs and platform. Scan those
   same bytes; bind reports to config IDs and manifests, source SHA, lockfiles,
   scanner version, DB timestamp, SBOM and report SHA256. Keep all findings visible.
2. Produce a review bundle. Each exception must include exact repository/service,
   AMD64 manifest digest (and index digest if present), source revision, CVE/GHSA,
   package identity/PURL, installed version and target, vendor evidence, exposure,
   compensating-control evidence, owner, reviewer, approval record, UTC issue and
   expiration times. No wildcard CVEs/packages, mutable tags or source-SHA-only
   exemptions. A Docker config ID alone is not a registry manifest digest.
3. A protected review stage, without self-approval, approves the bundle hash and
   each exception. A file merely claiming `approved: true` is not approval. Bind
   trusted GitHub approval evidence to the exact bundle. Deny if the repository's
   GitHub plan cannot enforce the required reviewers; no implicit fallback.
4. The default remains strict/public. The private policy requires explicit
   environment selection and a valid approved bundle. Fail on unknown schema,
   missing evidence, stale scan (propose 24 hours), future issue time, invalid or
   expired UTC time, duration over 14 days, wrong digest/platform/source/package,
   revoked/missing approval, or any unmatched HIGH/CRITICAL. Scanner/tool errors
   must fail, not produce an empty successful inventory. Public mode ignores all
   private exceptions and still rejects severe findings.
5. Publish the **same OCI bytes** with digest-preserving tooling only after
   approval; abort if the registry manifest differs. No rebuilding after review.
   Deploy by digest and revalidate approvals, expiration and controls before use.
   Bind the release manifest to policy/bundle hashes. Rollback also revalidates
   the target; a formerly approved but expired image is not automatically eligible.
6. Proposed deterministic tests: exact match accepted; one extra HIGH rejected;
   changed digest/package/version/platform/revision rejected; missing/revoked or
   self approval rejected; expired/boundary/malformed dates rejected; tool failure
   rejected; public mode rejects a private exception; scan/report hash mismatch
   rejected; registry digest mismatch rejected; expired rollback rejected.

No workflow, validator or exception configuration was changed for this proposal.
It must be approved before implementation, then tested before specific waivers
are considered. No exceptions are currently deployable.

## Minimum remaining steps, in order

1. Review this frontend checkpoint and decide whether to approve implementation
   of the private-demo policy design. That decision grants no individual waiver.
2. Close Mock SAP's old runtime exposure using a focused minimal-runtime candidate
   or individual evidence review. Finish effective JDK trust-store/graphics-path
   assessment for Keycloak and backend, plus UNKNOWN review. Stop rebuilding
   unchanged images. Prepare only justified, exact-image exception proposals.
3. Resolve or explicitly review the separate Keycloak console acceptance issue
   without weakening existing tests. Obtain authorized push and exact-revision CI
   and E2E, including Mailpit, on the release source. No current remote pass is
   claimed for these local changes.
4. After policy implementation is approved and verified, build/scan the final
   seven-image artifact bundle with fresh advisories. Obtain explicit approval for
   any proposed exceptions and publication. Existing publish workflow is main-only;
   source integration/merge requires separate authorization.
5. Only after release approval, authorize Contabo provisioning within the agreed
   budget. Apply/test IP restrictions, domain/TLS, secrets, reviewed SMTP, offsite
   restore, monitoring and actual 8 GB resource limits. Run protected production
   smoke tests before admitting testers. No real cloud actions were performed here.

Current verdict: frontend remediation is useful and verified within its stated
limits; **deployment is NO-GO today**. A narrow approved private-demo policy is a
reasonable path to a bounded demo, not permission to ignore findings indefinitely.
