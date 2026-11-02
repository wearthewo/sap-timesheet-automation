# Private-demo release policy: operator guide

The user approved implementation of this mechanism on 26 October 2026, **not any
individual vulnerability exception or deployment**. No real approval, reviewer key
or host configuration is installed by this change. Public SaaS continues to use
the unchanged `scan-verification.mjs` and `production-publish.yml`: every
HIGH/CRITICAL finding blocks publication.

## Trust and scope

The separate private path requires all of:

1. Successful exact-revision core CI and E2E, trusted main-branch candidate build,
   and independently protected GitHub `private-demo-publish` / `private-demo`
   environments with required reviewers and self-review disabled.
2. Full unfiltered Trivy reports and CycloneDX SBOMs for all seven AMD64 runtime
   images. Report/config/manifest/archive hashes must agree. Reports and advisory
   DB evidence must be no older than 24 hours and not future dated.
3. An Ed25519-signed approval from a reviewer in an **externally administered**
   trust configuration. A JSON field saying approved is insufficient. Private keys
   never enter this repository, workflows, artifacts or the deployment server.
4. Approval binding to the exact bundle hash, immutable image manifest digest,
   service, scan target/type, package/PURL/version, advisory and severity. The
   bundle binds the source SHA, platform, full scan and SBOM bytes. Approval is
   exclusively for the single `private-demo` environment, never public SaaS.
5. Vendor evidence, risk assessment, compensating controls, remediation plan,
   named owner and independent reviewer for each exception. Evidence snapshots
   are SHA256-bound and must be supplied unchanged. Approval covers every listed
   exception explicitly; duplicate, extraneous or unmatched exceptions fail.
6. Valid UTC timestamps, maximum 14 days, expiration exclusive (`now >= expiresAt`
   fails), no future issuance and no revoked approval ID. Current trust and expiry
   are checked again at deployment, including rollback and immediately before
   stopping existing services.

UNKNOWN severities or unrecognized advisory identities cannot receive exceptions.
They fail closed. All HIGH/CRITICAL findings must match a specifically signed
exception; findings remain present in the raw report. No ignored vulnerabilities,
VEX suppression, scanner disabling or business/authentication changes are added.
Malformed/missing reports, unsupported OS inventory and absent package identities
also fail. Runtime Java coverage still needs explicit review; an OS scan cannot
prove all JDK advisories are absent.

## Workflow sequence (requires separate operational authorization)

1. Run `private-demo-candidate.yml` on the reviewed main SHA. It builds once into
   OCI archives, validates paths/blob hashes, extracts a new layout for Trivy, scans
   all severities and generates SBOMs. No image is published. Failed scanner or
   build commands fail the run; partial evidence is retained for diagnosis.
2. Download the successful `private-demo-candidate` artifact. Review `bundle.json`,
   `scans/`, `sboms/` and the OCI archives. `private-demo-bundle.mjs archives DIR`
   checks archive hashes. The manifest digest is known **before** publication.
3. The risk owner prepares an unsigned approval document and evidence snapshots.
   The independent reviewer examines them and signs the exact UTF-8 document
   bytes with their externally held Ed25519 key. There is deliberately no command
   that auto-generates a real approval from scanner results.
4. Run `private-demo-publish.yml` with the successful candidate run ID, explicit
   confirmation, signed envelope and evidence map. Candidate SHA must still equal
   the workflow's SHA. Input secrets are forbidden: evidence must contain safe
   assessment/control records, not tokens, credentials or private keys. GitHub
   dispatch input-size limits apply; keep records concise.
5. The workflow downloads the candidate, verifies signature/trust and every
   binding, then checks archive hashes before registry login. Skopeo is pinned to
   `quay.io/skopeo/stable:v1.20.0@sha256:47853bb9fb24202af9110531ebd6e43c5f97701254ca290596640290d17942f4`.
   It copies with `--preserve-digests`, without rebuilding. Each registry manifest
   is rehashed and compared. A partial registry upload failure is a failed run,
   never a deployable release; it does not automatically delete registry data.
6. The `production-release` artifact includes raw scans/SBOMs, bundle, evidence,
   signed approval and immutable `release.json`. It excludes archives and trust
   private keys. The deployment workflow accepts only a successful matching
   publication workflow, checks exact-source CI again, and explicitly selects
   `private-demo` rather than the default public policy.
7. An operator installs the reviewed evidence bundle root-owned on the server at
   `/opt/workforce/evidence/<bundle-hash-without-sha256-prefix>/`. It contains
   `bundle.json`, `scans/`, `sboms/`, and `evidence/`. Host verification does not
   trust a bundle or reviewer key supplied through SSH stdin. The host's own
   policy/trust file and the signed release are verified before pulls and again
   after backup, before any running service is stopped.

OCI tar archives are not directly accepted by the pinned Trivy input reader.
`oci-inventory.py ARCHIVE NEW_LAYOUT` first validates file types, paths, all
referenced blob digests and AMD64 config, then writes only regular files into a
new directory. Traversal, duplicate members, symlinks, modified layers and existing
extraction destinations are rejected. Python 3 is needed on the candidate runner,
not on the deployment host for policy verification.

## Approval schema and signing

Use the exact field names enforced by `private-demo-policy.mjs`. An approval has:

```text
schema: 1
id: unique approval UUID
environment: private-demo
bundleHash: sha256 of the exact bundle.json bytes
owner: named risk owner, different from reviewer
reviewer: externally trusted reviewer ID
issuedAt / expiresAt: YYYY-MM-DDTHH:mm:ss.000Z (maximum 14 days)
controls: six evidence references, keyed by:
  ingress-allowlist, identity-authorization, synthetic-data,
  tls-outbound, backups-recovery, expiry-response
exceptions: individually reviewed entries containing:
  finding: service, imageDigest, target, type, package, purl,
           installedVersion, cve, severity
  vendorEvidence: {url: HTTPS source, sha256: snapshot hash}
  riskAssessment, compensatingControls, remediationPlan: substantive text
```

Each control also has `{url, sha256}`; its safe evidence snapshot must be at least
20 bytes. Snapshot files are named `evidence/<hex-sha256>.txt`. The reviewer must
check substance and real controls; schema/cryptography cannot prove that a written
claim about a firewall is true. Reject reachable exploits and unverified controls.

Sign outside automation, for example with an externally secured Ed25519 key:

```sh
openssl pkeyutl -sign -rawin -inkey /secure/reviewer-ed25519.pem \
  -in approval-payload.json -out approval.sig
```

Envelope: `{"keyId":"reviewer-id","payload":"BASE64_OF_EXACT_DOCUMENT_BYTES","signature":"BASE64_SIGNATURE"}`.
The dispatch `evidence_json` is a map from `sha256:<hex>` to base64 snapshot bytes.
No actual signed approval or production key is included in source. Tests generate
ephemeral keys in memory and use explicitly synthetic evidence only.

## External configuration and deployment installation

Configure GitHub environment variable `PRIVATE_DEMO_TRUST_JSON` separately for
publication and deployment. The trust schema is:

```json
{"schema":1,"environment":"private-demo","reviewers":[{"id":"INDEPENDENT_REVIEWER","publicKey":"EXTERNALLY_VERIFIED_ED25519_SPKI_PUBLIC_PEM"}],"revokedApprovalIds":[]}
```

The placeholder is intentionally invalid. Install actual public keys only after
out-of-band identity/fingerprint verification. GitHub environment controls and
host root administration are trust boundaries; repository authors cannot approve
themselves by adding a key to source. Rotate/revoke in both GitHub environments
and host configuration; no automatic key enrollment. Existing approval IDs can be
revoked without changing their signatures. Removing a reviewer also revokes trust.

In addition to existing tools, root-install `enforce-release-policy.mjs`,
`private-demo-policy.mjs` and the reviewed `release.mjs` together under
`/opt/workforce/tools/`. The new host entrypoint requires a root-managed
`/etc/workforce/release-policy.json`:

- Public installation: `{"mode":"public-saas"}`. Private approvals are rejected.
- Private installation: `{"mode":"private-demo","trust":<the external trust object>}`.

Missing/unknown mode fails; a private host rejects release JSON without signed
private evidence. A missing trust file/evidence fails before application mutation.
The deployment account must not write the tools, policy, evidence or source tree.
The signed release remains bounded to 64 KiB. Install files during an explicitly
authorized host setup; this checkpoint performs no installation.

## Expiry and operating controls

Freshness is intentionally stricter than approval duration: redeploying after
24 hours requires fresh evidence and a new reviewed bundle/signature, even if
the previous exception has not expired. No automatic renewal or retry bypass.
Rollback requires currently valid evidence too; plan it before an upgrade.

Publication/deployment checks do not stop a running service when time expires.
The signed expiry-response control must name the operator/alert and ingress-close
procedure. Before enabling a real demo, verify advance monitoring and close tester
access if patch/reapproval is not completed by expiry. Do not delete business data.
The user must still separately authorize real infrastructure, publication and
deployment. This mechanism authorizes none of them by itself.

## Verification limits

The implementation has deterministic positive/negative signature, finding,
expiry, digest, evidence and host-policy tests, OCI safety tests, and actual local
OCI round-trip/scanner checks. GitHub workflow execution, registry publication,
real reviewer protection settings, host installation and live IP restriction
remain **not executed**. Do not claim remote release readiness from local tests.
