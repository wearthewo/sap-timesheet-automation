# Phase 13 private-demo policy and runtime checkpoint

26 October 2026. Parent `923dd142486c5276688ca249b498ebeffe7ab714` on
`feature/phase12-production-readiness`. User approval covers **implementation and
testing only**. No individual vulnerability exception was approved. Deployment
remains **NO-GO** pending the exact blockers below.

## Implemented mechanism

See [the operator guide](private-demo-release-policy.md). Public scanner and
publication threshold are unchanged. Separate candidate/publication workflows
retain OCI artifacts and all findings; no scanner suppression exists. Private
approvals require an external Ed25519 reviewer, exact bundle/image/package/CVE
bindings, signed vendor/control evidence, maximum 14 days and explicit environment.
UNKNOWN findings are rejected without a waiver path. The same verifier runs at
publication and deployment, including host recheck after backup. Missing host
policy/trust/evidence fails closed. No actual reviewer keys or approvals are
configured by this commit.

Focused tests cover positive synthetic signed approval, public-policy rejection,
missing/revoked/substituted approval, self-review, expiration/boundaries, overlong
validity, modified evidence/report/SBOM, unmatched findings, wrong package/version/
digest/target/revision/platform, unknown findings, stale/future evidence, missing
OS coverage and expired rollback. Cryptographic approvals in tests use ephemeral
in-memory keys; they cannot authorize production images.

## Mock SAP runtime remediation

The actual production Mock SAP Dockerfile now uses the same verified distroless
Node24 base as the frontend:
`gcr.io/distroless/nodejs24-debian13:nonroot@sha256:cb606325b7ec4af55462cb0b424a53b452a5b4a894ffe841fcf1048d216fb9f8`.
It retains **UID/GID 1000** for existing state-volume and backup compatibility.
Only its token is read from the mounted secret; no shell or package manager is
needed. Compose health uses `/nodejs/bin/node`. HTTP behavior and simulator
business code are unchanged.

| Evidence | Result |
|---|---|
| Actual AMD64 Docker image ID | `sha256:b1b0c00db41105503b0f7b9c7416af761b04f955291ae93d906219eb439a2dfd` |
| Full image scan | **0 CRITICAL / 0 HIGH / 23 MEDIUM / 8 LOW / 0 UNKNOWN** |
| Report SHA256 | `c82468342044991120f22b7ad6dc43fb040c19cdf90998105d97676f57da833e` |
| Public retained inventory | [31 findings](phase13-mock-distroless-findings.csv) |
| Runtime checks | **9 passed**: health, denied unauthenticated request, creation, duplicate identity, UID/private file permissions, read-only root, SIGTERM exit 0, persisted lookup after restart, validation error |
| OCI conversion manifest | `sha256:f2279bcff85d910832518ef69daf715d3f597bc7037cb10d274587c9f9ec0d71` |
| OCI config ID | `sha256:4934af54201ad40cb9c13302326ae2fa79b9b30362efcb271e42db179d5c3a91` |

Scanner: checksum-verified Trivy0.76.0, existing DB timestamp
`2026-10-25T01:20:33Z`, all analyzers retained. This is comparative local evidence;
its age now exceeds the new 24-hour publication window. **A fresh release scan is
required**, not a waived freshness check. No language package manifests exist in
this built-in-only Node server; zero detected language packages is expected and
not a claim about Node's own advisories. Exact base/runtime version remains pinned.

One new labeled test volume and one synthetic container were used. Only those
test resources were removed after successful restart testing. Original application
containers, Keycloak realms, databases and volumes were not changed.

The Docker archive was converted locally to OCI to exercise the new tooling.
Conversion changes config/manifest identity; it is **not** a published release
digest. A subsequent `--preserve-digests` OCI round trip retained exact manifest
and config bytes. The OCI layout scan independently matched that config ID and
the same vulnerability counts. No registry was written.

## Focused Java and UNKNOWN assessment

| Finding | Evidence / result / remaining disposition |
|---|---|
| Backend CVE-2026-91403 | Actual pinned Temurin container executes Java21.0.12.1+1. `keytool` lists eMudhra G3/C3/C1/G1 aliases; the JRE `cacerts` is a regular bundled file dated August18, not the RPM OS-store linkage. Red Hat's Keycloak/RPM mitigation therefore **does not establish backend mitigation**. Outbound Java TLS needs a patched trust/runtime or separately verified vendor remedy. No exception approved. |
| Keycloak CVE-2026-91403 | Previous runtime probe found RPM OS-store linkage and no G3 alias in filtered output, but retained G1. Complete implicated-chain/fingerprint verification remains required before proposing vendor-mitigated acceptance. No unsupported JDK replacement was performed. |
| CVE-2026-91415, Java2D | Vendor describes crafted graphics/font input. Source search found no ImageIO/BufferedImage/multipart upload or autoSizeColumn calls; report generators create POI font records and fixed column widths. That does not prove every transitive JVM path unreachable. Remains an individually reviewable risk, not approved. |
| CVE-2026-39824 / GO-2026-5024, gosu x/sys | Official Go advisory identifies `golang.org/x/sys/windows.NewNTUnicodeString`, fixed in0.44.0. Reviewed helper imports `x/sys/unix`; current binaries are linux/amd64. Evidence supports **Windows-only code not used in this Linux helper**, not a demonstrated Linux exploit. However the requested private policy rejects UNKNOWN severity; no automatic exception or severity relabeling is added. A supported dependency patch/fresh scan is the clean closure. |
| GO-2026-5932, Caddy x/crypto | Official Go advisory concerns the unmaintained `openpgp` subpackages, not all x/crypto consumers. Module presence does not prove this path is linked/called. Full transitive reachability was not established; UNKNOWN remains rejected. Do not replace x/crypto wholesale or suppress the module. |
| CVE-2026-107891, musl/musl-utils | Existing reports provide no title/description/reference. Recorded patched versions are1.2.5-r24 (Caddy) and1.2.6-r3 (PostgreSQL/backup). Bounded vendor-page lookups did not establish the affected behavior. Insufficient evidence for acceptance; UNKNOWN remains blocked. No unchanged image was rebuilt or rescanned merely to reproduce the finding. |

Primary references:

- [Red Hat CA advisory](https://access.redhat.com/security/cve/cve-2026-91403)
- [Red Hat Java2D advisory](https://access.redhat.com/security/cve/cve-2026-91415)
- [Go Windows advisory](https://pkg.go.dev/vuln/GO-2026-5024)
- [Go OpenPGP advisory](https://vuln.go.dev/ID/GO-2026-5932.json)

The previously verified Temurin21.0.13+9 binary is a potential patch route, but
no new vendor container or supported custom runtime has been compatibility-tested
here. No security clearance is inferred from OS scanner coverage alone.

## Keycloak registration JavaScript investigation

A fresh read-only browser session against the unchanged `localhost:33000` again
reached the registration form and reproduced the null `addEventListener` error.
No user was registered and no existing data changed in this diagnostic.

The exact upstream26.8.1 `keycloak.v2/login/register.ftl` conditionally renders
password fields when `passwordRequired` exists but unconditionally invokes
`validator.script field="password"`. The macro in `password-validation.ftl`
unconditionally attaches the listener. The configured email-first registration
flow has no password field at that first step, explaining the exception.

- [Upstream registration template](https://raw.githubusercontent.com/keycloak/keycloak/26.8.1/themes/src/main/resources/theme/keycloak.v2/login/register.ftl)
- [Upstream password macro](https://raw.githubusercontent.com/keycloak/keycloak/26.8.1/themes/src/main/resources/theme/keycloak.v2/login/password-validation.ftl)

Earlier real-browser evidence on the unchanged Keycloak image completed new-user
registration and the related flows, despite the error. The user's successful manual
registration is consistent with that evidence. The earlier helper recorded **five
groups PASS / one FAIL** at its global no-console-error assertion; this was not
evidence that registration itself was universally broken.

The new `workforce` theme inherits `keycloak.v2` and overrides only the pinned
vendor password macro. The single-character correction uses `?.addEventListener`
when the password element is absent. A regression test reverses that character
and compares the exact vendor template SHA256; a second verifies both absent and
present password elements. Password policy, server validation and OIDC are unchanged.
Vendor attribution/license and an upgrade-removal note accompany the override.

The final candidate Keycloak image is
`sha256:8388272a75eb34cde81f16ada13a783cc0cae5477403d33dcf705f7faecc544e`.
Its JDK/OS layers are unchanged; **this is a functional correction, not elimination
of the two JDK HIGH findings**. No new security clearance is inferred. Fresh realm
imports select the theme. An existing realm requires an explicitly authorized
operator theme selection; existing local realms were not changed.

Focused real-browser verification against the actual candidate completed **6/6
groups, zero uncaught page errors**, covering SSR/health, email-first registration,
email verification, password setup, OIDC callback, organization creation, BFF
reload, secure HttpOnly cookies, CSV/XLSX downloads, invitation registration and
acceptance, employee download denial, password recovery, new-session login and
mobile logout. No assertions were removed and no timeout was increased.

One preceding attempt passed SSR then failed at onboarding: the frontend logged
`503 SERVICE_UNAVAILABLE` and the backend subsequently recorded user provisioning.
This establishes a backend-request availability failure, not a new registration
or template failure. A cold-path delay is plausible but its exact cause was not
proven. The unchanged helper passed on one subsequent run. Both result files are
retained locally; **cold-start reliability remains a tracked limitation**, and this
is not a claim that the complete application suite passed on this revision.

## Corrections discovered during mechanism verification

- Trivy rejects an OCI tar as direct input. Added validated extraction to a new
  OCI layout directory; real scan then passed. Initial failed command is retained
  as tooling failure, not counted as a clean result.
- OCI metadata/config identities differ from Docker archive identities after
  format conversion. Approval binds actual OCI bytes and scan config ID, not a
  guessed registry digest or prior Docker image ID.
- Candidate workflow uses a container buildx driver because the default Docker
  driver does not universally support OCI export. No local QA builder/stack was
  replaced. Actual full GitHub candidate build remains unexecuted.
- Deployment now needs explicit operator-installed policy and evidence. Missing
  setup blocks before mutation rather than silently reverting to a permissive mode.
- OCI validation uses explicit checks rather than Python `assert`, so `python -O`
  cannot disable digest, platform or descriptor validation. All seven safety tests
  also pass with optimization enabled.

## Remaining release blockers

1. Java CA/JDK findings and retained UNKNOWNs are not approved or eliminated.
2. The focused Keycloak console gate now passes. Initial backend-request availability
   remains a tracked reliability risk; exact-revision CI/E2E must verify the release.
3. Fresh seven-image exact-revision scans, source CI/E2E and actual GitHub workflow
   executions are pending authorization. Local reports are not fresh release proofs.
4. Real independent reviewers, external public-key trust, any individual exception
   approvals, host policy/evidence and expiry-response monitoring are not configured.
5. Contabo host/IP allowlist (both hosts and IPv4/IPv6), TLS, secrets, reviewed SMTP,
   encrypted offsite restore and operational smoke acceptance remain deployment
   prerequisites. No provisioning, purchase, publication, merge or deployment occurred.

The mechanism is implemented; it does not make the release pass while these
conditions remain. Proceed with a bounded patch/review of these exact findings,
not another general infrastructure rebuild or repeated full application test run.

## Final focused verification and checkpoint scope

Executed with scoped Node24.22.1 (no global runtime changes):

```text
node --test infra/ci/ci.test.mjs infra/production/production.test.mjs
  infra/production/line-endings.test.mjs infra/production/release.test.mjs
  infra/production/monitor.test.mjs infra/production/frontend-entrypoint.test.mjs
  infra/production/private-demo-policy.test.mjs infra/production/mock-entrypoint.test.mjs
  infra/production/keycloak-theme.test.mjs mock-sap/server.test.mjs
python -O infra/production/oci-inventory.test.py
actionlint
bash -n infra/production/deploy-release.sh
docker compose --env-file infra/production/production.env.example
  -f infra/production/compose.yaml config --quiet
node infra/production/mock-runtime-check.mjs
node infra/production/frontend-browser-check.mjs
git diff --check
```

Wrapped command lines above are single invocations. The final Node run passed
**149 unique tests, 0 failures, 0 skips**. OCI passed **7 unique tests**, including
under Python optimization; earlier normal/optimized runs are not added together.
Actionlint, Bash syntax and production Compose configuration passed. Mock runtime
passed **9 checks**; browser acceptance passed **6 groups on the second attempt**
as detailed above. Full backend/Phase 12F suites were deliberately not repeated.

Additional bounded commands used the existing checksum-verified Trivy0.76.0,
`image --input <archive-or-validated-layout> --skip-db-update --skip-java-db-update
--scanners vuln --format json`, and pinned Skopeo `copy --preserve-digests` between
local OCI archives. The failed direct OCI-tar scan and corrected layout scan are
distinguished above. Docker build of `infra/production/keycloak.Dockerfile` reused
unchanged optimized/JDK layers and added only the reviewed realm/theme files.
Read-only `java -version` / `keytool -list -cacerts` probes established the backend
trust-store evidence. No backend image was silently substituted.

Docker, native image scans, browser execution, candidate diagnostics and Git index/
commit operations required outside-sandbox execution. Scoped Node/Python checks
ran locally; no global installations were changed. All **17 original containers
remained running and healthy** after only the isolated six-service candidate was
stopped. Its PostgreSQL used tmpfs, not original volumes. No original identity,
database, volume or realm was changed. No external email was sent.

Changed-file inventory:

- `.gitattributes`, `.gitignore`; CI quality checks; new private candidate and
  publication workflows; policy-aware production deployment workflow.
- `private-demo-policy.mjs` and tests; bundle/evidence/release tools;
  `enforce-release-policy.mjs`; `release.mjs`, `release-gates.mjs`,
  `deploy-release.sh`; OCI inventory/extraction and its tests.
- Production Mock SAP Dockerfile, secret bootstrap/tests, runtime check,
  empty owned data directory, Compose health executable, production regression test.
- Production Keycloak Dockerfile/realm, inherited theme override/license/README,
  deterministic vendor-template and handler regression tests.
- Private policy operator guide, this implementation report, Mock scan inventory,
  proposal status, Contabo readiness and deployment-guide updates.

Generated scans, browser results, synthetic credentials and private verification
files stay ignored. Tests use ephemeral signing keys; there is no real approval
or private key in the commit. Public scanner/publication files are unchanged.

**Ready for authorized exact-revision GitHub source CI:** yes, as a reviewed local
checkpoint. Remote workflow execution is not claimed. Publishing is still blocked
by unresolved findings, freshness and absent explicit approvals. **Private-demo
GO/NO-GO: NO-GO.** Do not dispatch publication/deployment merely to bypass these
remaining controls. The current checkpoint is a local commit only, on
`feature/phase12-production-readiness`; its exact hash is reported in the handoff.
