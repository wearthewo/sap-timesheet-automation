# Phase 13 input-path assessment — 29 October 2026

**Public launch: NO-GO. No vulnerability decision is approved.** This continues the investigation after `8ed2394`; it does not replace raw findings or the signed public-production policy. No production image, application code, realm, database or deployment setting changed. Existing images and downloaded sources were reused.

## Proposed classifications

| Finding | Classification | Remaining action |
|---|---|---|
| Proxy GO-2026-5932, OpenPGP, UNKNOWN | **CANDIDATE NOT AFFECTED** | Existing exact compiled dependency evidence excludes affected OpenPGP packages. Independent review and signature for the final manifest; no module-wide exclusion. |
| Proxy CVE-2026-116587, Go JOSE v3.0.5, HIGH | **CANDIDATE NOT AFFECTED, configuration-specific** | The optional ACME-server route can reach private-JWK validation, but production does not instantiate it or an internal issuer. The public ACME client uses a different path. Review the image AND effective configuration. The library itself remains vulnerable. |
| Keycloak CVE-2026-91403, certificate store, HIGH | **VERIFIED REMEDIATION, approval pending** | Existing vendor checker/default trust-manager evidence verifies fingerprint-specific removal from the actual Java store. No custom trust-store override configured. This does not claim every OS bundle was regenerated. |
| Keycloak CVE-2026-91415, Java2D, HIGH | **BLOCKED** | QR generation loaded no font classes, but vendor information does not identify the exact vulnerable function sufficiently to exclude all graphics processing. Obtain a supported runtime fix or independent affected-API/distribution review. |
| Keycloak CVE-2026-102554, Guava 33.6.0-jre, HIGH | **BLOCKED** | Direct Keycloak paths were traced; Kerberos rejects affected collection families and Twitter reads server-generated data. This is not a complete proof for every transitive native-deserialization path. Obtain a supported Keycloak update or independent closure of the remaining paths. |

No public exploit was demonstrated in this application; that is different from proving non-applicability. No hostile-font or memory-exhaustion payload was executed. Quotas and memory caps do not automatically make reachable memory corruption or tiny-payload OOM acceptable.

## Exact evidence scope

| Image | OCI manifest (release identity) | Local configuration ID executed |
|---|---|---|
| Proxy | `sha256:f046885d046218b888f969b0c632f1d0695883b9e5def3bf32df00c539ba5c61` | `sha256:43edc0f8d6b440194692399cdc04b8e27fe66f1d5171c7cc28d7be138bfb35db` |
| Keycloak | `sha256:a117385d0366500e088293cf03c2275d03a05b708ed91286a1e825b4195b3637` | `sha256:64fc6a7800f64594166fa97a6213af40d2abd557231aa32ef1eae2415cc79d22` |

Both inspected images are AMD64. The archive/configuration relationship and scan/SBOM hashes are retained in the [public-policy verification record](phase13-public-policy-verification.md). No supported replacement was established, so no unchanged image was rebuilt/rescanned. Previous totals remain proxy **1 HIGH / 1 UNKNOWN**, Keycloak **3 HIGH / 0 UNKNOWN**, both zero CRITICAL. These are not fresh seven-image release results.

The probe adapts the repository production Caddyfile using the exact proxy image and synthetic public hostnames; it opens no listener and contacts no CA. SHA256 of inspected working-tree configuration bytes:

| File under `infra/production/` | SHA256 |
|---|---|
| `Caddyfile` | `4515ca1fa1fc9bdf5943b1587f4e7394b9bb8338da9eba72a2c9ce25ab2c9743` |
| `compose.yaml` | `042adb0c9e7e20ad7153e2c8e7fb46d578741543c4b96fd6742764062a1bf1ea` |
| `workforce-realm.json` | `10ff2b8b5f997dbe99deb1f9a70d71521e47f537fc36d8e63595523bb3f58d3f` |
| `keycloak.Dockerfile` | `c254be5c86c9f6db3ca6aa6978919d267e8b9c449a49d269198edce63dff5976` |

Hashes include checkout line endings. The reviewer must bind the final effective configuration. An import template does not prove a running realm's current state. Later custom providers, IdPs, JVM filters, trust stores, cache or ingress changes invalidate assumptions and require reassessment.

## Go JOSE: source-to-input trace

Versions: Caddy **2.11.7**, Smallstep certificates **0.30.2**, Smallstep crypto **0.91.0**, Go JOSE **v3.0.5** (separate v4 occurrence already **4.1.6**), acmez **3.1.7**, certmagic **0.25.6**.

1. Standard Caddy modules include PKI and its optional ACME-server handler. [`acmeserver.go`](https://github.com/caddyserver/caddy/blob/v2.11.7/modules/caddypki/acmeserver/acmeserver.go), `Provision` line 126, calls `CA.NewAuthority` around line 192 and registers Smallstep routes. [`ca.go`](https://github.com/caddyserver/caddy/blob/v2.11.7/modules/caddypki/ca.go):222 constructs the authority; [`internalissuer.go`](https://github.com/caddyserver/caddy/blob/v2.11.7/modules/caddytls/internalissuer.go):108 also uses it.
2. If the ACME **server** is enabled, [`middleware.go`](https://github.com/smallstep/certificates/blob/v0.30.2/acme/api/middleware.go):96–109 reads the request body and calls `jose.ParseJWS`. Smallstep [`types.go`](https://github.com/smallstep/crypto/blob/v0.91.0/jose/types.go) aliases Go JOSE v3 types and delegates parsing. RSA-public-key checks in `validateJWS`:160–174 occur **after parsing**, so are not a mitigation.
3. Go JOSE [`jwk.go`](https://github.com/go-jose/go-jose/blob/v3.0.5/jwk.go):215–217 selects `rsaPrivateKey()` when RSA parameter `d` exists. Lines 612–654 construct the key and invoke `PrivateKey.Validate()` without patched size bounds. The [vendor advisory](https://github.com/go-jose/go-jose/security/advisories/GHSA-mgcc-cww5-fp2q) still lists no fixed v3 release; v4 is fixed in 4.1.6.
4. Actual production adaptation confirms `admin.disabled=true`, no `apps.pki`, no `acme_server` handler, no configured internal issuer and no on-demand issuance. Enabled handlers: headers, log_append, request_body, reverse_proxy, static_response, subroute, workforce_guard. Requests go to frontend/Keycloak proxies; arbitrary Host values do not create configured sites.
5. Public certificate issuance uses [`ACMEIssuer`](https://github.com/caddyserver/caddy/blob/v2.11.7/modules/caddytls/acmeissuer.go):327–337 → certmagic [`acmeclient.go`](https://github.com/caddyserver/certmagic/blob/v0.25.6/acmeclient.go) → acmez. Its [`acme/jws.go`](https://github.com/mholt/acmez/blob/v3.1.7/acme/jws.go):119–170 encodes the public portion of a locally held account key, rather than parsing an incoming private JWK through Smallstep.

**Conclusion:** the optional vulnerable path exists in the binary, but no configured public entry point to it was identified in this routing/issuance setup. This supports independent configuration-specific non-applicability review, not patched-library status or generic Caddy clearance. Enabling an ACME server, internal issuance, JWK-consuming plugin or admin API requires reassessment. No module was removed to silence scanning.

## Java2D: established boundaries and remaining gap

Installed JDK: `java-21-openjdk-headless 1:21.0.12.1.1-1.2.el9`; `java.desktop` is present. Headless does not remove graphics code.

[`TotpUtils.java`](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/utils/TotpUtils.java):65–78 constructs an OTP URI using realm/user data with fixed 246×246 dimensions. [`QRCodeUtils.java`](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/utils/QRCodeUtils.java):23–27 converts text to a ZXing bit matrix and writes PNG bytes. User text becomes QR data, not a font file. The exact-image benign Unicode/HTML-like text probe generated a PNG without loading `sun.font.*` or `java.awt.Font` classes.

No Keycloak production `Font.createFont` or `ImageIO.read` call was found in the source search. Transitive JAR references nevertheless include JGroups demos, JTS FontGlyphReader, H2 console, ZXing image readers, JAXB image handling and SAAJ image handlers. Demo/client/deployment artifacts are not automatically public endpoints. Keycloak's SAMLDataMarshaller:81–98 uses SAMLParser's StAX path (SAMLParser:108–122), not a demonstrated uploaded-image decoder.

**Still blocked:** [Red Hat's advisory](https://access.redhat.com/security/cve/cve-2026-91415) describes affected 2D APIs broadly, including data read/modification consequences. One QR execution without font classes cannot exclude every affected graphics API or binding. October 29 metadata still marks RHEL 9 java-21-openjdk affected; the latest official Keycloak release checked is **26.8.1**. No supported patched replacement was established. No JDK transplant was made. The earlier backend JRE applicability question also remains open despite its scanner identification limitation.

## Guava: native deserialization, filters and origins

The [Guava advisory](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94) fixes native-deserialization allocation in **33.7.2**. Keycloak contains **33.6.0-jre**. Vendor Moderate versus scanner HIGH is recorded without downgrading the gate.

| Path | Origin/filter | Result and limit |
|---|---|---|
| [KerberosSerializationUtils](https://github.com/keycloak/keycloak/blob/26.8.1/common/src/main/java/org/keycloak/common/util/KerberosSerializationUtils.java):69,107–121 | Base64 native stream; Kerberos classes and java.util allowlist, then deny-all | No production caller found beyond the utility definition in inspected Keycloak sources. Actual installed filter rejected benign CompactHashMap, CompactHashSet and MapMakerInternalMap streams; permitted HashMap control succeeded. Unfiltered positive controls showed the Guava classes are present/deserializable. This closes this filter path only. |
| [TwitterIdentityProvider](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/social/twitter/TwitterIdentityProvider.java):101–126,237–238 | Login serializes a typed RequestToken into a server authentication-session note; callback reads that note | No local ObjectInputStream allowlist, but bytes are not taken directly from the callback body. Production template configures no Twitter/external IdP. Do not enable it under this assessment or call the deserializer universally safe. |
| [DefaultDataMarshaller](https://github.com/keycloak/keycloak/blob/26.8.1/server-spi-private/src/main/java/org/keycloak/broker/provider/DefaultDataMarshaller.java) | Typed JSON/Base64; SAML specialization parses XML | These mechanisms do not themselves invoke native Guava readObject. JSON is not a Java serialization stream. |
| Infinispan/JGroups/Vert.x/Narayana transitive paths | Cluster/cache transport, shared-data codecs, transaction recovery/storage; native classes present | Keycloak registers ProtoStream schemas; cluster-event DB bytes are not direct HTTP bodies. Production exposes neither database/cache ports nor Quarkus dev mode. This is still **not** a codec-by-codec proof against request-controlled bytes reaching a native fallback. |

[DelegatingSerializationFilter](https://github.com/keycloak/keycloak/blob/26.8.1/common/src/main/java/org/keycloak/common/util/DelegatingSerializationFilter.java):47–51 installs its allowlist only when the stream has no existing filter. Adding a broad global `jdk.serialFilter` casually could displace stronger per-stream protection. **No workaround was added.** The probe checks the default global filter is absent. A maxarray setting alone does not prove limits on library arrays allocated from attacker-controlled integer fields.

Additional reviewed source: DefaultCacheEmbeddedConfigProviderFactory:197 invokes Marshalling.configure; schema initializers do not prove all native fallbacks disabled. DatabaseClusterEventPollerTask:70–85 reads stored events through the configured marshaller. `application/x-java-object` cache encoding alone does not establish ObjectInputStream use. These distinctions prevent overstating both risk and safety.

**Shortest supported resolution:** a Keycloak-supported dependency update or upstream/security review closing those specific codec/input paths. The standalone Guava patch is not a compatibility-tested supported Keycloak distribution. No unsupported JAR transplant, permissive filter or scanner exclusion was made.

## Focused verification and reproducibility

New probes: `infra/production/security/InputPathProbe.java`, `infra/production/security/input-path-check.mjs`. Use project Node 24 and already-present local image IDs:

```powershell
.verification/runtime/node-v24.22.1-win-x64/node.exe infra/production/security/input-path-check.mjs sha256:43edc0f8d6b440194692399cdc04b8e27fe66f1d5171c7cc28d7be138bfb35db sha256:64fc6a7800f64594166fa97a6213af40d2abd557231aa32ef1eae2415cc79d22 .verification/input-path-review/runtime-final
```

Final run started **2026-10-28T23:10:55.567Z / October 29 Europe/Athens**. Configuration checks and **5/5 benign Java probes passed**: allowed control, three Guava rejections, QR output. No font classes loaded by the probe. These are standalone calls to installed classes, **not** a running-server/browser or complete transitive call-graph test. Application, E2E and 143 policy tests were not repeated.

Harness failures disclosed: initial handler list omitted existing `log_append`; initial Java test classpath omitted Keycloak boot JARs and failed with `NoClassDefFoundError: org/jboss/logging/Logger`. Both harness defects were corrected without changing production. The subsequent four-probe run passed; adding the third Guava family produced the final five-probe pass. Caddy emitted existing header/default-format warnings, not adaptation errors.

Docker ran outside sandbox: `--rm --pull=never --network=none --read-only`, AMD64, caps dropped/no-new-privileges, one CPU/384 MiB/64 PIDs, read-only probe mount and temporary `/tmp`. No existing container/volume changed or database mounted. Four missing exact-tag source files and two official metadata responses were fetched with bounded TLS-verified requests outside sandbox after Windows sandbox TLS credential failure. No completed image/source archive was redownloaded, rebuilt or rescanned.

Retained ignored evidence (SHA256; none is a signed approval):

| Path under `.verification/input-path-review/` | SHA256 |
|---|---|
| `runtime-final/runtime-evidence.json` | `2df3315dcb37b8c50b45640977c0f895c2057027bb24470875c4d38c82f60ef3` |
| `runtime-final/keycloak-probe.txt` | `634e02f7ee78bcb7835778ef33eac53be6de2b479ac32641dc83854ca8ec61f7` |
| `runtime-final/proxy-adapt.txt` | `6448cce3cbb59151e1b16b38920267cd22008de74c94892564dba3db8586aaf1` |
| `jar-audit.json` | `bfce01454ba1ae996728cbbca2569cde597401d6c6f7ed03a2442b88479d662d` |
| `keycloak-latest-20261029.json` | `fdca6c46e5144c0335b8cbad05ae248162c525456b2040da9c2e155e9bb11bfc` |
| `java2d-20261029.json` | `b6e3da7b9cc0d205f28e271baef2347efbdd21bdcb3e131266a793c89e47be76` |

Existing exact-layer JAR audit: **487 JARs / 91,840 classes / 60 JARs with selected references**, including client/deployment/demo artifacts. Constant-pool references identify candidate sites, not reachability; reflection/native code and configured codecs require separate review. Temporary scripts were reviewed for bounded downloads, regular selected files and traversal rejection. No downloaded third-party source or generated JAR inventory is committed.

Retained source archive SHA256: Keycloak 26.8.1 `f7763d73c33a2d345227522aa51a5ead3b7f0fde2a807704933174b8a3628b5c`; Caddy v2.11.7 `86e39de5fa0bc433a9cd574a00e7751059903401f3389c61f8937fd0ad0180f5`; Smallstep crypto v0.91.0 `19ff1e93afe506884f799c512b3ce8b4e0dc84a70fb6f60e77c53d01596f9ce1`. Additional exact-tag files/hashes in `remaining-sources/inventory.json`: JOSE jwk `79f510a4b97fa2e34ec37932cab57e64dfdc9fa6c314654d1ccc2dd93898a532`; Smallstep middleware `a974a08c1e52f81035fb3e4231d15f923ef11bc7240d0628a7b84ffda095fe71`; acmez jws `5eda9e02b6847d5305272d0f8cd4e11e90baafb825abc4ec7ac783d3348f7e10`; certmagic client `4217baa468a6ba19d12ac77611f58cb9b39224e8f25cf322259db521cd38134f`.

## Minimum remaining work

1. Independently review OpenPGP, configuration-specific Go JOSE and Java trust-store remediation. Close Java2D's affected-API question and Guava's remaining native-codec paths or obtain supported fixes. No blanket risk waiver or self-signature.
2. This documentation/probe-only checkpoint is ready for separately authorized exact-revision GitHub verification. No application behavior changed; another local full application/E2E cycle is unnecessary. Historical CI does not attest the new commit or final images.
3. Once technical decisions are resolved and execution authorized, generate final seven-image scans/SBOMs/provenance once. Signed decisions must match final digests, evidence, assumptions and expiry. Publication remains separately authorized and blocked until clearance.
4. Keep the existing operator runbook. Actual DNS/TLS, clean-host Resend SMTP (local Avast interception is not a provider defect), B2 restore, firewall/alerts, secrets and production smoke checks remain pending. No purchase or deployment is justified by this report alone.

No production pin changed. This is a narrower, reproducible review package—not security clearance or a release authorization. The two pre-existing untracked audit reports were preserved and excluded from the checkpoint.
