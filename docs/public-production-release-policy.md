# Public-production release assessment

Implemented 28 October 2026 with authorization to build/test the mechanism only. **No vulnerability is approved. Publication and deployment remain NO-GO.** This reuses the existing signed private-policy primitives, OCI archive verifier and deployment hook. It is not a private-demo exception path.

29 October update: explicitly configured **schema 2 solo-maintainer** approval is supported. Schema 1 remains independent; existing private-demo policy remains independent and does not accept schema 2. This changes who may sign supported technical determinations, not the required evidence or the raw scanner gate. See the single-maintainer procedure below.

## Decisions and boundaries

The raw scanner gate remains unchanged: UNKNOWN/HIGH/CRITICAL fail. The explicitly selected public assessment path retains every raw finding and requires a signed determination under externally configured trust covering each exact image manifest digest, service, target, package, PURL, installed version, vulnerability ID and severity. Independent mode requires a different owner and reviewer. Solo mode requires the trusted signer to be the named owner and forbids every RISK_ACCEPTED decision, including LOW/MEDIUM residual-risk waivers.

| Disposition | Required evidence / bound |
|---|---|
| NOT_AFFECTED | Vendor and exact-runtime evidence of absent affected code/path; no residual impact. Maximum 30 days. |
| REMEDIATED | Vendor-supported mitigation verified in the exact runtime; no residual impact. Maximum 30 days. |
| RISK_ACCEPTED | Independent mode only. Explicit narrow approval, restricted exposure and limited availability impact only. LOW/MEDIUM/HIGH eligible; never UNKNOWN or applicable CRITICAL. Maximum 14 days. |
| TRACKED / baseline | Enumerated LOW/MEDIUM only, reviewed evidence of no residual impact and a remediation/reassessment deadline. Maximum 30 days. |

A signed baseline groups exact LOW/MEDIUM finding records under one review, evidence and deadline. It needs no separate manual approval per finding. It is not a wildcard for future findings. Missing findings, duplicate coverage, unmatched/different digests, HIGH/UNKNOWN baseline entries, missing evidence or unacceptable exposure fail. Authentication bypass, tenant disclosure, code execution, memory corruption and service exhaustion cannot receive residual-risk acceptance regardless of severity. The verifier checks signed assertions and evidence integrity; it cannot replace the reviewer's technical judgment or prove a written claim true.

## Bundle and trust contract

The shared candidate workflow (`private-demo-candidate.yml`, historical filename/artifact name retained) with `policy: public-saas` builds all seven AMD64 OCI archives once, verifies archive structure/layer hashes and retains full Trivy inventories, CycloneDX SBOMs and source revision labels. No image is published. Schema 2 records general and Java database provenance. General DB and scans must be at most 24 hours old; Java metadata must be current before its next update and span no more than seven days. Public input requires full package lists. Raw reports are never edited. Scanner coverage limitations still require reviewer investigation, especially JDK advisories missing from binary inventories.

The original signed approval payload has exactly `schema: 1`, `id`, `environment: public-production`, `bundleHash`, `owner`, `reviewer`, `issuedAt`, `expiresAt`, `controls`, `decisions`, `baselines`. UTC timestamps use `YYYY-MM-DDTHH:mm:ss.000Z`. Schema 1 owner and reviewer must differ. Its external trust file has `schema: 1`, `environment: public-production`, trusted `reviewers: [{id, publicKey}]` (Ed25519 PEM public keys) and `revokedApprovalIds`. Schema 2 adds `approvalMode` to BOTH payload and external trust, with value `independent` or `solo-maintainer`. Schema and mode must match exactly; there is no fallback or implicit opt-in. The retained `reviewer`/`reviewers` field names identify the signer(s), not a claim of independence in solo mode. Keys and revocations are managed independently of candidate artifacts. No production signing key is generated or stored in this repository or Actions.

Required controls: `public-ingress`, `identity-authorization`, `tls-outbound`, `backups-recovery`, `expiry-response`, `runtime-configuration`. Each references `{url: HTTPS source, sha256: exact evidence hash}`; retained evidence bytes live at `evidence/<64-hex-hash>.txt` (20–65,536 bytes). Record the reviewed Compose/environment/template hashes and actual runtime constraints in configuration evidence; the verifier binds that evidence, but does not automatically compare the live host configuration. The operator must verify this correspondence before release.

An individual decision contains `finding`, `disposition`, `exposure`, `impact`, `unacceptableExposure: false`, `vendorEvidence`, `technicalEvidence`, `assessment`, `compensatingControls`, `remediationPlan`, `expiresAt`, `remediationDueAt`. A baseline has the same review fields, replacing `finding`/`disposition` with `findings: [...]`; its disposition is always TRACKED. See the synthetic test fixture for field structure, not real approval content. Deadlines must be future, within the signed assessment expiry and within the applicable maximum validity.

The Ed25519 envelope is `{keyId, payload, signature}` with canonical base64 payload bytes and signature. Do not reserialize the payload before verification. All seven image evidence hashes and assessments are covered by the signature. No current HIGH/UNKNOWN finding has a signed disposition.

## Authorized operator sequence (not executed)

1. Review/commit source, then separately authorize pushing. CI and full-stack E2E must pass the exact final revision, with all required jobs successful. Configure `production-publish` and `production` environments. Independent mode requires independent required reviewers and prevention of self-review. Solo mode uses the explicit trust setup below and manual main-only dispatch; no second engineer is required. Unsupported required environment configuration still blocks automation.
2. After separately authorized merge, dispatch the shared candidate on main with `policy: public-saas`. It uses the `production-publish` environment so candidate preflight and publication read the same external trust variable. Download `private-demo-candidate`; inspect complete raw scans, SBOMs, OCI provenance and runtime evidence. The authorized reviewer/owner prepares and signs the public assessment. This workflow intentionally has no automatic classifier or signer.
3. Supply the protected environment variable `PUBLIC_PRODUCTION_TRUST_JSON` identically in both environments. Publish the **non-secret** transport document `{approval: signedEnvelope, evidence: {"sha256:...": "base64 bytes"}}` in a reviewed evidence repository at an immutable GitHub raw commit URL. Do not include tokens, personal data or secrets. `assessment_url` accepts only commit-pinned raw.githubusercontent.com or gist.githubusercontent.com HTTPS JSON; redirects are refused, download is bounded to 2 MiB/15 seconds. `assessment_sha256` binds the entire document. Its hash alone is not approval.
4. Only with publication authorization, dispatch `production-publish.yml` with candidate run ID, URL, document hash and `PUBLISH PUBLIC PRODUCTION`. Candidate must be successful on main at the exact source revision. The shared verifier checks signatures/evidence; archive hashes are checked before `skopeo --preserve-digests`. Every remote manifest must match the reviewed digest. No rebuild occurs during publication. Partial publication is not deployable. Retain the `production-release` artifact (30-day Actions retention is not permanent audit storage; archive it durably).
5. Before deployment, root installs the four reviewed `.mjs` tools listed in the operator runbook. Independently install `/etc/workforce/release-policy.json` as root:root 0600 with `{"mode":"public-saas","trust":<same public trust object>}`. Install verified `bundle.json`, `approval.json`, `scans/`, `sboms/`, `evidence/` under `/opt/workforce/evidence/<bundle hash without sha256:>/`, root-owned and non-writable by the deployment account. Do not trust keys delivered alongside evidence. No tar archive or private key is required on the host for authorization.
6. With separate deployment authorization, dispatch `production-deploy.yml` using policy `public-saas` and the successful publishing run. GitHub and the host both call `authorizePublicProduction`; the host checks before pulling and again after backup immediately before changing services. Private approvals cannot satisfy it. Release JSON contains only bundle/envelope hashes plus immutable image inventory, not a large embedded approval.

Fresh scan limits apply even if an assessment lasts 14/30 days: stale evidence blocks a later deployment or rollback. Refresh scans and obtain a newly bound assessment when needed; do not alter an old signed bundle. Expiry/revocation does not automatically stop an already-running application. The required expiry-response plan must assign an operator, independent alerts and remediation/isolation before the deadline. Configure that operational monitoring before launch.

## Single-maintainer setup and operation

1. Hold the owner signing private key offline/outside Actions and the VPS. Record a stable owner identifier. An owner signature proves explicit authorization and integrity, not independent assurance or correctness of a technical claim. A compromised/mistaken sole owner remains a risk; this mode does not simulate two-person control.
2. Explicitly configure `PUBLIC_PRODUCTION_TRUST_JSON` in **both** existing GitHub environments (`production-publish` and `production`) with the same schema 2 object. Install that same object as `trust` in root-owned `/etc/workforce/release-policy.json` under `mode: public-saas`. Never take trust from an uploaded candidate, assessment URL, SSH release manifest or workflow input. The following is an intentionally invalid placeholder, not configured trust or a generated key:

   ```json
   {
     "schema": 2,
     "environment": "public-production",
     "approvalMode": "solo-maintainer",
     "reviewers": [{"id": "maintainer-id", "publicKey": "REPLACE_WITH_OWNER_ED25519_PUBLIC_PEM"}],
     "revokedApprovalIds": []
   }
   ```

3. Restrict environment branches to main and protect repository/admin access with MFA. Solo mode does not require GitHub's second-person reviewer rule; it requires a real public release environment, manual `workflow_dispatch`, main, valid externally supplied Ed25519 trust and the later signed assessment. Typed publication/deployment confirmations remain. Candidate preflight is **not** approval to publish. Environment variables must be supported by the repository's plan; unavailable settings are not silently bypassed. Private release environments retain their independent-review requirement.
4. Inspect raw scans, source provenance, final digests, SBOMs and vendor/runtime evidence. Use NOT_AFFECTED only with affirmative absence-of-affected-path evidence and REMEDIATED only with verified effective remediation. Missing proof, unresolved impact, missing findings and unsupported severe/UNKNOWN baseline treatment still block. LOW/MEDIUM exact baselines retain their existing evidence/deadline requirements. No RISK_ACCEPTED decision is allowed in solo mode.
5. Prepare schema 2 assessment with `approvalMode: solo-maintainer` and `owner == reviewer ==` the configured signatory ID. All existing fields, control evidence, hashes, dates and limits remain. Sign exact payload bytes using Ed25519 and retain the existing envelope/transport format. Publish only the non-secret signed transport at its immutable URL. No automatic signing was added; review is an explicit human action.
6. Run the existing publication procedure. Deployment uses the same signed bundle; the host independently verifies its externally installed key, matching mode/schema, revocation, freshness and exact digests before pulling and again before service changes. A different host key/mode fails even if publication succeeded. Replacing an environment's trust does not update host trust; both require deliberate operator changes. Old schema 1 approvals cannot authorize a schema 2 host.
7. Preserve the signed assessment, raw scans, evidence and release manifest durably. Revoke by approval ID in both trust stores; rotate keys deliberately. Schedule expiry alerts and re-review or remediation before the deadline. No mode automatically shuts down a running service on expiry.

Existing finding status is unchanged: OpenPGP and configuration-specific Go JOSE are technical NOT_AFFECTED candidates; certificate-store correction is a REMEDIATED candidate. The owner still must inspect final-image evidence and explicitly sign. Keycloak Java2D and Guava remain blocked pending verified fixes or sufficient additional technical evidence. **No real determination, trust key or release authorization was created by this implementation.**

## Focused local verification

Node 24.22.1 command:

```text
node --test infra/production/public-production-policy.test.mjs infra/production/public-assessment-input.test.mjs infra/production/private-demo-policy.test.mjs infra/production/release.test.mjs infra/production/production.test.mjs
```

143/143 passed, no failures/skips. Includes signature/key substitution, self-review/revocation, private/public mismatch, missing/tampered/stale evidence, UNKNOWN/CRITICAL risk rejection, exact findings/digests, baseline boundaries, URL transport checks and publication/host parity. Synthetic keys/findings only. This is mechanism verification, not approval of actual runtime findings or execution of GitHub publication/deployment.

Additional focused checks: `python infra/production/oci-inventory.test.py` 7/7 passed (archive/digest/extraction guards); `node --test infra/production/line-endings.test.mjs` 2/2 passed; pinned local `actionlint` passed; `git diff --check` passed; `docker compose --env-file infra/production/production.env.example -f infra/production/compose.yaml -f infra/production/backup.compose.yaml config --quiet` passed. Compose emitted a sandbox Docker configuration access warning; it did not contact or change services. No application suite, image build, publication, provisioning or deployment was performed for this policy change. No database or volume was modified.

29 October solo-mode verification: the same five-file Node 24.22.1 command above passed **176/176**, zero failures/skips (33 added tests). New coverage exercises public/host acceptance and rejection, signed mode/schema matching, owner identity, missing/untrusted/duplicate keys, revocation, expiry, missing/tampered evidence, exact digests, signature tampering, candidate-supplied key rejection, unassessed HIGH/CRITICAL/UNKNOWN, prohibition of all solo risk waivers, main/manual environment preflight and unchanged independent/private behavior. Tests use ephemeral synthetic keys/findings, never actual vulnerability approvals. No application or image build was needed.

Also rerun for this change: `node --test infra/production/line-endings.test.mjs` **2/2 passed**; existing local pinned `actionlint` passed all workflows; `git diff --check` passed. Node came from `.verification/runtime/node-v24.22.1-win-x64/node.exe`, not a global toolchain change. No CI/E2E dispatch, new image scan, Docker service mutation or approval signing occurred. The next authorized GitHub run must verify the new policy commit; the earlier 413/16 application results belong to the previous revision.
