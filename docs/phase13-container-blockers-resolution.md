# Phase 13 — final container blocker corrections

27 October 2026 (Europe/Athens). Parent source: `e500eb39f8276c161f5fe19d8858f96cc0adb12d`. This checkpoint changes runtime packaging only. Application CI/E2E was not repeated; no application code, realm, database schema, worker schedule or security policy changed.

**Private-demo publication and deployment: NO-GO.** Four image corrections are verified locally. Two Keycloak HIGH findings and one proxy UNKNOWN remain. No exception was approved and no image was published.

## Adopted corrections

- Proxy: curl/libcurl **8.23.0-r0**, musl/musl-utils **1.2.5-r24**, retaining the pinned Caddy/Go bases, Caddy binary sources, Brotli correction and forwarding configuration. Removes CVE-2026-92392 and proxy CVE-2026-107891 occurrences.
- PostgreSQL and backup: musl/musl-utils **1.2.6-r3**; gosu remains upstream 1.20 runtime source, with `golang.org/x/sys` **0.44.0** and checksum-verified module files. Removes all six prior UNKNOWN occurrences across these two images, including CVE-2026-39824. No package was concealed or excluded. Go 1.27.3 remains pinned; module metadata now declares that compiler version. Upstream provenance distinguishes modified dependency metadata from unchanged Go source/license.
- Backend: `java-trust-mitigation.sh` removes the two present implicated/precautionary roots (G3/G1), verifying exact certificate fingerprints before deletion. G2/AAA are absent in this pinned base. The JRE, non-root user, entrypoint and application JAR remain unchanged. The default TrustManager now trusts 116 issuers, versus 118 originally; C1/C3 remain. Vendor checker exits 0 with no bad roots. Default `cacerts` store password is a public JDK convention, not an application secret.

Immutable base pins in `images.lock.json` are unchanged. Supported repository package updates and verified Go module checksums supply the corrections; builds fail if those exact versions/checksums are unavailable. This is a maintained Workforce image build, not a claim of upstream-signed Caddy/PostgreSQL output.

## Complete changed-image scans

Trivy **0.76.0**, all vulnerabilities and package inventories retained; no ignore file or package exclusions. General DB updated `2026-10-27T01:25:30.944314632Z`; Java DB updated `2026-10-27T01:08:30.264956307Z`. Both were fresh at scanning. All changed images have validated AMD64 OCI archives, complete scan output and nonempty CycloneDX inventories; scanner configuration IDs match the extracted OCI configurations.

| Changed image | Scan time, Athens (+02:00), 27 Oct | CRITICAL | HIGH | UNKNOWN | MEDIUM | LOW |
|---|---|---:|---:|---:|---:|---:|
| Proxy | 13:30:41.6135282 | 0 | 0 | 1 | 2 | 0 |
| PostgreSQL | 13:32:19.9055876 | 0 | 0 | 0 | 1 | 0 |
| Backup | 13:33:34.6266534 | 0 | 0 | 0 | 1 | 0 |
| Backend | 13:35:52.9439944 | 0 | 0 | 0 | 28 | 16 |

Frontend and Mock SAP remain unchanged at 0 CRITICAL/HIGH/UNKNOWN; their completed scans were reused. Keycloak remains unchanged at 0 CRITICAL, 2 HIGH, 0 UNKNOWN (55 MEDIUM, 35 LOW). Across the seven images, the previous 4 HIGH/9 UNKNOWN package occurrences are reduced to **2 HIGH/1 UNKNOWN**. A clean scanner result alone is not complete JRE applicability clearance.

### Exact candidate OCI manifest digests

| Image | Digest |
|---|---|
| Proxy | `sha256:8a534dd124bf88ec9a336f07fc3c7d31f51df589cc66709aae3b807da07c033e` |
| PostgreSQL | `sha256:af605ec661af656a795a1545fbc219ecf5dc3e1389ce11eccaf7da8ae8f6a418` |
| Backup | `sha256:f46e52c1be6b9e086e51174b993bc7d9eb1ed4b9ece4386bf187f435a1a3ac47` |
| Backend | `sha256:59d50d50be0a5d63a344465368ffb931dffd679eaee8426813d3626d4e61e162` |
| Keycloak, unchanged | `sha256:b0fcae7c3d959dbf14aa55e28e1a628597b4d019cf792e2b5b49d1e7f7ad1907` |

Local evidence root: `.verification/runtime-blockers-20261027/`. `inventory.json` records scan/SBOM SHA256 values and OCI configuration digests; `source.json` records build input hashes and parent revision. Original evidence under `.verification/release-e500eb3/` is preserved. No candidate archive or sensitive verification data is committed.

**Provenance limit:** these images were built from the reviewed working tree, before its checkpoint commit. Labels explicitly identify an uncommitted runtime candidate and source hash, rather than asserting final committed-release provenance. Only `gosu/UPSTREAM.md` documentation changed after the input snapshot; executable inputs match. These are local verification artifacts, not a signed GitHub release bundle. A future authorized trusted workflow must build the final committed revision and supply its own exact digests. Native scan timestamps have offsets; current approval validation requires UTC `Z` evidence from the trusted workflow. Raw reports have not been rewritten.

## Proxy UNKNOWN: investigation result, not a waiver

**GO-2026-5932**, `golang.org/x/crypto` **v0.57.0**, remains UNKNOWN in Trivy. The [official advisory](https://pkg.go.dev/vuln/GO-2026-5932) affects the unmaintained `openpgp` subpackages and lists no fixed version.

Ran `go list -mod=readonly -deps .` with the exact pinned Linux/AMD64 Caddy build stage: **1,028 imported packages, zero `golang.org/x/crypto/openpgp` packages or descendants**. Caddy does use other x/crypto packages (OCSP, bcrypt, SSH and other primitives), so removing the module or hiding its build metadata would be inappropriate. Dependency graph SHA256: `9bef51c6a0aaca52ae910701ccdc14f87b9f9d0833fdb95b022ae3334e54dd78`.

This establishes a module-level overmatch for this specific build's import graph. It is independently reviewable evidence of absent affected packages, not a scanner exclusion or signed exception. The production policy **rejects all UNKNOWN records** and has no approved not-affected/VEX path; therefore the gate still blocks this image. No module fork, version spoofing, package deletion, advisory suppression or policy change was introduced to manufacture a green result.

Resolution requires upstream scanner/advisory precision, or a separately proposed and explicitly authorized evidence-based policy extension for independently verified not-affected records. The latter is not implemented or approved by this checkpoint. An ordinary HIGH exception cannot override the existing UNKNOWN check.

## Keycloak: supported fix and mitigation assessment

Read-only official release and registry checks still identify **26.8.1**, published 13 October, and the **same pinned base index digest** `sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b`. No newer official patched image was available in those checks. No unverified JDK transplant was attempted.

Both HIGH records identify `java-21-openjdk-headless` **1:21.0.12.1.1-1.2.el9**; the current scan gives no fixed package version:

| Finding | Actual runtime evidence | Reviewable mitigation / remaining decision |
|---|---|---|
| CVE-2026-91403 | Default store resolves to `/etc/pki/ca-trust/extracted/java/cacerts`; implicated G3 and AAA roots are absent. G1 remains and causes the broader Oracle CheckCerts test to exit 1. | [Red Hat](https://access.redhat.com/security/cve/cve-2026-91403) says RPM runtimes consume the OS CA store and removal of implicated roots can mitigate the flaw. This supports independent review of this exact digest, but does not authorize treating the scanner record as cleared. The broader G1 precaution remains explicit. Do not disable certificate or hostname verification. |
| CVE-2026-91415 | Java2D/font flaw requires affected graphics-processing APIs. No image/font-upload feature or graphics-processing API was identified in the reviewed Workforce/Keycloak configuration; ordinary browser font rendering is separate. | [Red Hat's advisory](https://access.redhat.com/security/cve/cve-2026-91415) supports assessing exposed graphics-input paths. No reachable exploit was demonstrated, but absence of a discovered path is not proof of all Keycloak/transitive code being unreachable. IP restriction reduces exposure but is not a patch. A supported fix or independent, time-limited risk decision remains necessary. |

[Keycloak documents](https://www.keycloak.org/server/containers) a UBI installroot pattern for package additions and copying updated CA material, but that does not by itself establish that a particular replacement JDK/package set fixes these CVEs or preserves compatibility. No supported patched package combination was verified here. Review may accept a narrow exact-digest mitigation after checking effective trust and permitted inputs; this checkpoint does not approve that decision.

The same retained Temurin Java version in the backend needs Java2D applicability considered during independent review. The trust-store fix addresses certificate trust only. No `ImageIO`, `BufferedImage`, `Font.createFont`, `autoSizeColumn` or `MultipartFile` use was found in backend application sources; this is bounded source evidence, not blanket JRE security clearance.

## Focused verification

No full application tests, backend regression, browser acceptance or unchanged-image rebuilds were run.

- `docker run ... <pinned Go image> sh -ec 'go mod edit -go=1.27.3 -require=golang.org/x/sys@v0.44.0; go mod tidy; go mod verify'`: passed; checksum database enabled.
- `docker buildx build --platform linux/amd64 --load ... -f infra/production/{caddy,postgres,backup,backend}.Dockerfile .`: four successful builds using cached application/compiler stages. Archived with `docker save`; pinned Skopeo converts to OCI; `python infra/production/oci-inventory.py ...` validates each archive.
- Trivy `image --input <OCI-layout> --parallel 2 --timeout 5m --skip-db-update --skip-java-db-update --ignorefile '' --config '' --scanners vuln --list-all-pkgs --format json`; separate `--format cyclonedx`: four successful full scans/SBOMs using the fresh databases above.
- Scoped Node 24: `node --test infra/production/production.test.mjs infra/production/line-endings.test.mjs`: **18/18 passed**, zero skips/failures.
- `node infra/production/runtime-candidate-check.mjs proxy <immutable-local-image-ID> --live-readonly`: **6/6** component checks passed, including certificate-verified TLS, forwarding sanitization, actual read-only QA login/health/discovery, body limits, HTTP/2 and graceful shutdown.
- Same checker, `postgres`: **5/5** passed: production constraints/health, isolated least-privileged database initialization, gosu drop/settings, decimal dump/restore and shutdown. Uses newly created tmpfs data, not application volumes.
- Backup container: **4/4** existing restore guards passed; age encryption/decryption roundtrip, privilege drop, shell syntax and PostgreSQL 17.11 dump tool check passed. Network disabled; synthetic files exist only in tmpfs.
- Backend: non-root `with-secrets` executed vendor CheckCerts with a synthetic secret file under a read-only filesystem; passed. TrustProbe confirmed the default store. SHA256 of application JAR is identical to the pre-change image. No production SMTP message or database call was made.
- `git diff --check`: passed. No scanner/policy/workflow assertions changed. Existing policy and OCI safety test evidence remains applicable because those files are unchanged.

Docker/network-dependent commands used approved outside-sandbox execution. No global toolchain changes. Temporary verification containers/networks were removed by their scoped checkers; no original volume was mounted, reset or removed.

## Commit scope and next action

Reviewed files: four production Dockerfiles; `java-trust-mitigation.sh`; gosu `go.mod`, `go.sum`, `UPSTREAM.md`; this report; Contabo readiness checkpoint. The two earlier untracked public-audit/final-clearance reports are preserved separately, not silently included in this runtime correction.

The changes are suitable for a local checkpoint and focused CI review; **they are not a cleared private-demo release**. No automatic exception approval, publication, merge, push, purchase, DNS change or deployment is authorized. The next decision is independent review of the three retained scanner records and backend JRE applicability, plus an explicit decision about how to handle a proven not-imported UNKNOWN under the fail-closed policy. GitHub environment protections and trusted exact-revision publication provenance remain prerequisites already documented in Phase 13.
