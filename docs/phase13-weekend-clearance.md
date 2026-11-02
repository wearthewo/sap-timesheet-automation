# Weekend public launch clearance — 28 October 2026

**PUBLIC-PRODUCTION NO-GO.** Policy implementation is complete locally at `260e1766e9db5b426cef4f1a1e8559bf3606e602`. This is not approval of the five outstanding findings. No production images were published, infrastructure purchased, DNS changed or application deployed.

October 29 continuation: [source-level input-path assessment](phase13-input-path-assessment.md). The optional Go JOSE path is now traced and proposed as configuration-specific not affected; Java2D and Guava remain blocked. Five benign exact-image Java probes and proxy adaptation checks passed. No security approvals were issued. Results below are the preserved October 28 checkpoint, not reruns.

## Completed repository work

- Reviewed and finished the interrupted changes, committed 18 explicit policy/workflow/documentation files. Reused the existing Ed25519 and OCI verifier. Publication and the deployment host enforce the same public assessment; private approvals are rejected. The strict raw scanner still rejects HIGH/CRITICAL/UNKNOWN.
- Signed baseline groups enumerate exact LOW/MEDIUM findings without individual approvals. New/unmatched findings, missing inventories, invalid signatures, modified evidence, expiry and digest mismatches fail closed. No live approval or signing key was created. See [the policy/operator guide](public-production-release-policy.md).
- Publication consumes the reviewed seven-image archive set without rebuilding and checks registry manifests. Public deployment requires external reviewer trust and root-installed evidence, not merely a mode flag. The Contabo runbook is corrected accordingly.
- Existing public-registration limits, 12 GB configuration, Resend secret-file settings, B2 encrypted backup upload/read-back, restore guards and monitoring remain intact. No application business code, migrations, authentication rules or existing runtime containers changed.

## Five individual findings

The scanner severities below are retained. These are review proposals, **not signed decisions**.

| Finding / exact installed component | Supported fix / applicability evidence | Required resolution before public release |
|---|---|---|
| Proxy **GO-2026-5932 UNKNOWN**, `golang.org/x/crypto v0.58.0` | Go advisory covers only OpenPGP subpackages, no fixed versions. Exact proxy build records its imports and fails if OpenPGP appears. Reading the final image confirmed no affected import. Removing unrelated x/crypto functionality is unnecessary. | Strong candidate for independently reviewed **NOT_AFFECTED**, bound to the final image and compiler dependency evidence. Scanner record remains. Any later image must be reassessed. |
| Proxy **CVE-2026-116587 HIGH**, `github.com/go-jose/go-jose/v3 v3.0.5` | Vendor still lists no patched v3; v4 fix is 4.1.6 and already adopted for the separate v4 occurrence. Both current Smallstep crypto/certificates manifests still require v3.0.5. A crafted RSA private JWK triggers costly validation. Final proxy imports v3 through Smallstep. Admin is off and no client JWK handler is configured, but this is not complete transitive reachability proof. | Supported upstream migration/backport plus focused compatibility/scan, **or** independent exact-configuration call-path proof of non-applicability. Do not assume the v4 update fixes v3. Reachable unbounded CPU exhaustion is not eligible for the limited-availability waiver. |
| Keycloak **CVE-2026-91403 HIGH**, `java-21-openjdk-headless 1:21.0.12.1.1-1.2.el9` | Fingerprint-checked Java CA removal is already in the image. The vendor checker again reports no bad roots; default trust manager has 117 issuers, no affected roots. Fresh Red Hat metadata says non-portable RPMs use ca-certificates and root removal is sufficient; its RPM fix state is deferred to that package. No custom trust-store override is configured in production Compose. | Candidate for independently reviewed **REMEDIATED** for the actual Java TLS store, with vendor guidance and checker evidence. This is not a claim that every OS CA bundle was rebuilt. Verify any added custom store and real SMTP on a clean host. |
| Keycloak **CVE-2026-91415 HIGH**, same JDK | Fresh Red Hat metadata still marks RHEL 9 `java-21-openjdk` affected with no fixed release in the record. Vendor describes graphics/font processing and possible out-of-bounds write, data read/modification, hang/crash. No supported patched Keycloak image was identified. A headless runtime or absent visible font-upload form alone is not proof of non-applicability. | Supported fixed runtime/image, or independently verified absence of an attacker-controlled graphics/font path in the exact distribution/configuration. If reachable, **technical fix required**; memory corruption/data compromise cannot receive residual-risk acceptance. Also close backend JRE applicability despite incomplete JDK scanner identification. |
| Keycloak **CVE-2026-102554 HIGH**, `com.google.guava:guava 33.6.0-jre` | Guava 33.7.2 fixes uncontrolled allocation through native Java `ObjectInputStream` deserialization. Ordinary JSON is not that input format. Vendor rates it Moderate; scanner HIGH is retained. Actual Keycloak jar remains 33.6.0-jre. Latest official Keycloak is still 26.8.1; no supported bundled correction verified. | Supported Keycloak integration/update, or independent proof that public/untrusted inputs cannot reach the affected native deserialization path. A reachable tiny-payload JVM OOM is service exhaustion, not a deadline-based waiver. No jar transplant or scanner exclusion. |

The official Keycloak 26.8.1 manifest was checked again: `sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b`, identical to the pinned base. No unchanged image was rebuilt or rescanned. A different vendor's JDK or Red Hat product being marked not affected does not demonstrate a supported drop-in fix for this Keycloak image.

### Evidence references

- [Go OpenPGP advisory](https://pkg.go.dev/vuln/GO-2026-5932).
- [Go JOSE advisory and patch ranges](https://github.com/go-jose/go-jose/security/advisories/GHSA-mgcc-cww5-fp2q); current [Smallstep crypto manifest](https://raw.githubusercontent.com/smallstep/crypto/master/go.mod) and [certificates manifest](https://raw.githubusercontent.com/smallstep/certificates/master/go.mod) inspected October 28. Moving upstream pages are investigation pointers, not immutable signed evidence.
- [Oracle/OpenJDK root-removal guidance and checker](https://ops.java/emudhra/); [Red Hat certificate record](https://access.redhat.com/security/cve/cve-2026-91403), [machine-readable record](https://access.redhat.com/hydra/rest/securitydata/cve/CVE-2026-91403.json).
- [Red Hat Java2D advisory](https://access.redhat.com/security/cve/cve-2026-91415), [machine-readable record](https://access.redhat.com/hydra/rest/securitydata/cve/CVE-2026-91415.json).
- [Guava advisory](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94), [official Keycloak 26.8.1 release](https://github.com/keycloak/keycloak/releases/tag/26.8.1).

Local, ignored evidence: `.verification/public-policy-20261028/evidence.json` records exact commands, runtime log hashes and retained scan/SBOM hashes. Red Hat snapshots: Java2D SHA256 `b6e3da7b9cc0d205f28e271baef2347efbdd21bdcb3e131266a793c89e47be76`; trust SHA256 `c52f4ad53af9d44fdba8084a1c3b00c491b516917c56c80b81a16dce65f90a7e`. Capture reviewed excerpts and immutable evidence in the final signed bundle; none has been signed here.

## Immutable candidate inventory and limitations

| Candidate | OCI manifest digest | Preserved full scan |
|---|---|---|
| Proxy | `sha256:f046885d046218b888f969b0c632f1d0695883b9e5def3bf32df00c539ba5c61` | 2026-10-28 14:22:19 +02:00; **0 CRITICAL / 1 HIGH / 1 UNKNOWN / 2 MEDIUM** |
| Keycloak | `sha256:a117385d0366500e088293cf03c2275d03a05b708ed91286a1e825b4195b3637` | 2026-10-28 09:46:44 +02:00; **0 CRITICAL / 3 HIGH / 0 UNKNOWN / 55 MEDIUM / 35 LOW** |

Source/SBOM/report hashes and Docker configuration IDs are in [the public readiness report](public-production-readiness.md). OCI manifest digests are not Docker configuration IDs. Runtime checks used those exact configuration IDs from the validated archives. These remain local pre-checkpoint candidates, not a newly built seven-image release from the final commit. Policy-only changes did not change their runtime contents. The other five historical scans were not re-labelled as fresh clearance. Final seven-image digests, SBOMs and signed provenance remain **BLOCKED/PENDING final candidate preparation and review**; there is no complete cleared production image set yet.

## Exact focused results this continuation

| Check | Result |
|---|---|
| Node 24 public/private policy, assessment transport, release and production configuration tests | **143/143 PASS**, no skips/failures |
| `python infra/production/oci-inventory.test.py` | **7/7 PASS** |
| Node Windows/LF checkout tests | **2/2 PASS** |
| Pinned `actionlint`; staged whitespace check | **PASS** |
| Gitleaks staged review, redacted | **0 leaks**, 18 intended files; ignored evidence and two pre-existing audit reports excluded |
| Production Compose + backup profile configuration | **PASS**, no service start; sandbox emitted a Docker config access warning |
| Exact proxy dependency evidence | **PASS**, affected OpenPGP imports absent; v3 JOSE remains present |
| Exact Keycloak vendor CheckCerts | **PASS**, no bad roots |
| Exact Keycloak default TrustProbe | **PASS**, 117 issuers, no affected root |
| Exact Keycloak RPM/jar inventory and non-root UID | **PASS**, installed vulnerable versions confirmed, UID 1000 |
| Full application/E2E | **NOT RERUN**, intentionally unchanged scope |
| GitHub execution of new policy workflows | **NOT RUN**, push/publication unauthorized |

Four runtime checks used short-lived `--rm --pull=never --network=none --read-only` containers, caps dropped, 384 MiB/one CPU/64 PID caps. Java probes used a read-only test-class mount and temporary `/tmp`; no database/network volumes mounted. These checks required outside-sandbox Docker execution. The bounded official manifest/advisory lookup also ran outside sandbox. All 17 existing containers remained healthy at the initial inspection; no existing container or volume was changed. No full build, package upgrade or fresh scan was warranted by a supported replacement identified in this continuation.

## Shortest legitimate path / weekend gate

1. **BLOCKED:** independent security reviewer closes OpenPGP and Java CA determinations against final exact images; closes the three remaining reachability questions or requires supported fixes. Do not purchase on a promise that this will complete by Friday.
2. **READY FOR AUTHORIZED CI:** review the local policy commit and this report. Separately authorize push and run required backend/frontend/Mock SAP/quality CI and full-stack E2E at the final source revision. Existing e500eb3 results do not verify the new revision. No additional local application test cycle is requested.
3. **BLOCKED UNTIL REVIEW/CI:** after separately authorized merge/candidate run, build and scan all seven final AMD64 images once, retain all findings/inventories, obtain exact-digest signed decisions and LOW/MEDIUM baseline. Configure independent GitHub reviewer protections and trusted public keys. Reuse the reviewed OCI archives for separately authorized publication; no self-approval or private-demo substitution.
4. **REQUIRES REAL HOST/SERVICE:** only after security clearance and spending authorization, follow the [October 31–November 1 runbook](contabo-deployment.md). Contabo VPS 6/Ubuntu 24.04, Namecheap app/auth DNS, real TLS, production secrets, clean database/realm, Resend domain verification/quotas, clean-host SMTP, private B2 key/bucket/lifecycle and actual encrypted restore, external heartbeat/alert delivery, firewall and reboot/rollback acceptance remain unverified. Local Avast interception is not a Resend defect and must not be imported into production trust.
5. **PUBLIC OPENING NO-GO until acceptance:** verify production HTTPS/authentication/registration, tenant isolation, outbound mail, backups/restore, alerts and resource headroom on the actual host. Define operator response before assessment expiry; verifier expiry blocks releases, not an already-running site automatically.

No extra development phase is proposed. The remaining work is specific technical/security review, exact-revision release evidence and unavoidable real-host acceptance. Weekend launch cannot honestly be guaranteed while the three applicability/fix questions remain open.
