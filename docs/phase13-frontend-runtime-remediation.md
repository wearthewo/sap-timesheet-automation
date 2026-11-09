# Phase 13 frontend runtime checkpoint — 26 October 2026

Parent: `b313a2eb1bc4e99682288dcb3d640ac31c958200`, branch
`feature/phase12-production-readiness`. **Complete release: NO-GO.** Frontend
image findings have been eliminated at the HIGH/CRITICAL threshold; Keycloak
still fails it. No security exception, suppression or release-gate change is
approved. This checkpoint does not repeat Phase 12F or Caddy/PostgreSQL remediation.

The later complete-inventory review also confirmed an existing Mock SAP scan
with 1 CRITICAL/56 HIGH findings on its unchanged Bookworm runtime. See the
[private-demo implementation record](phase13-private-demo-implementation.md); the
remaining release scope must not be described as Keycloak alone.

## Actual image, not just a base-image result

The unchanged frozen pnpm/Next.js build stage is retained. Its cached build was
assembled into the new runtime using the actual production Dockerfile, not a
fixture server. No application dependency, business code, realm or migration changed.

| Evidence | Immutable identity / result |
|---|---|
| Distroless Node 24 Debian 13 nonroot index | `sha256:cb606325b7ec4af55462cb0b424a53b452a5b4a894ffe841fcf1048d216fb9f8` |
| AMD64 child manifest | `sha256:4d0e659ef1f628c75d9940766cb444fa9605cc190b13676b9e62e1fa567c5cf4` |
| Pulled base local image ID | `sha256:ec563c6cd18654396f7b506d0b87b0357b741f7b86ad074f1190d8a3380b60a1` |
| Assembled frontend local image ID | `sha256:48877d8dcf830aaf24429526f83ef87114b5c26cc604b383161ac62eb9aa9e6d` |
| Runtime execution | Node 24.22.1, AMD64/x64, UID 65532, glibc 2.41 |
| Final complete scan | **0 CRITICAL, 0 HIGH, 23 MEDIUM, 8 LOW** |
| Scan inventory | 14 OS packages; 20 detected Node packages, zero detected Node vulnerabilities |
| Raw report SHA256 | `f74365c28e3bcbf1b7c6d0c03945c97c51829b986fbafd976e053766c8053303` |

Scanner: checksum-verified native Trivy 0.76.0, vulnerability DB updated
`2026-10-25T01:20:33Z`. Raw evidence is ignored under
`.verification/phase13-runtime-options/`; the
[31-row public inventory](phase13-frontend-runtime-findings.csv) retains every
finding, installed version and advisory. No OS/language analyzers, packages,
unfixed findings or severities were excluded. `scan-verification.mjs` accepted
the complete image report without modification. The prior base-only result was
not substituted for this scan.

The old frontend had 1 CRITICAL/56 HIGH findings. Its two additional detected
Node packages were global **corepack and yarn**; the 20 detected application
packages remain identical. Package metadata scans do not prove that all bundled
JavaScript is vulnerability-free. Existing lockfile/production dependency CI
gates remain required. All-image publication scans must use fresh advisory data.

Distroless avoids the unnecessary shell, package managers and utilities rather
than hiding their package inventory. The retained findings cover libc6, GCC
runtime libraries and zlib; they have not been suppressed or declared harmless.
See [upstream distroless documentation](https://github.com/GoogleContainerTools/distroless).
The index includes ARM64, but **only AMD64 was built and executed here**.

## Runtime changes and compatibility

- Runtime COPY ownership and USER are `65532:65532`.
- A small Node bootstrap replaces the shell entrypoint for this service only.
  It loads the two required mounted secrets before importing the standalone
  server, rejects missing/short/placeholder/invalid values, and never prints them.
  Other services retain their existing shell loader.
- Compose health uses `/nodejs/bin/node`; the shell-free root has no PATH alias
  named `node`. Existing limits, read-only root, capabilities, tmpfs and networks
  are unchanged.
- Build images still use the pinned Node 24.22.1 Bookworm root. A separate
  `frontendRuntime` lock entry prevents confusing build and serving images.
- The bootstrap tests are included in the existing GitHub quality job. No job,
  test, security assertion or vulnerability threshold was removed.

Observed component checks: health passed; non-root identity passed; `/app`
write rejected with EROFS; `/tmp` write/remove passed; shell/npm absent; memory
limit 805306368 bytes (768 MiB) and ALL capability drop retained. Missing mounted
secrets caused exit 1 before server startup. Native Sharp 0.35.5/libvips produced
a 97-byte PNG on glibc 2.41, using Next.js's actual pnpm dependency resolution.
An initial `require('sharp')` probe used the wrong top-level import path; resolving
from `require.resolve('next')` passed without changing dependencies.

Bounded stop of only the candidate frontend completed before its 30-second
deadline with exit 143 (SIGTERM), OOMKilled=false; no forced SIGKILL was observed.
This verifies signal termination, not draining a long-running request. Final
production Compose `config --quiet` passed. The six temporary candidate containers
and their two networks were removed with project-scoped `compose down` without
`-v`; their new databases were tmpfs-only. All 17 preexisting containers remained
healthy. Original application data, realms and volumes were preserved.

## Focused real-browser evidence and failures

Only disposable candidate dependencies were started, using existing local image
IDs. The project was `workforce-frontend-check-3c8d75c654`, with fresh tmpfs-only
PostgreSQL databases, synthetic secrets/identities and its own Mailpit. No original
database, realm, volume or application container was reset/recreated. No external
email was sent. Chromium trusted the isolated Caddy local CA only through its
test-context certificate override; production TLS settings were not weakened.

The existing production frontend service configuration was exercised. The reused
Phase 12F backend artifact runs on Alpine, so the **test fixture only** used its
curl health probe instead of the Jammy production image's Bash probe. Local SMTP
was explicitly configured for isolated Mailpit, not production SMTP.

| Executed browser group | Observed result |
|---|---|
| SSR with JS disabled, health, hydrated controls, anonymous `/app` redirect | PASS |
| Keycloak registration, verified email, PKCE callback, organization creation/reload through BFF | PASS |
| Mounted-secret authentication, secure HttpOnly session/organization cookies, no browser bearer tokens | PASS |
| Authorized CSV/XLSX browser downloads | PASS; MIME, attachment header, filename, non-HTML CSV and XLSX ZIP signature checked |
| Invitation email, new account acceptance, EMPLOYEE download denial | PASS; actual UI plus 403 from BFF |
| Password recovery, new-session login and mobile logout, with global JS-error assertion | **FAIL** at the final JS-error assertion; preceding recovery/login/logout actions completed |

**The complete browser helper is not green.** The last two full focused executions
each logged five PASS groups and one FAIL group. Do not describe them as a
successful full browser suite or silently remove the final error assertion.
No Phase 12F/full Playwright/backend suite was repeated.

The two errors were independently reproduced by opening the **unchanged**
application's public registration page at `localhost:33000`, without submitting
the form or creating a user. Both runtimes expose the same Keycloak 26.8.1 inline
registration error:

```text
Cannot read properties of null (reading 'addEventListener')
document.getElementById("password").addEventListener(...)
```

The email-first registration form has no password input at that step. The source
is Keycloak's `/realms/workforce/protocol/openid-connect/registrations` page, not
Next.js. Registration still reaches email verification, the subsequent password
form and the dashboard. This is a documented existing **Keycloak UI defect**, not
proof of a distroless regression. It remains an unresolved console acceptance
finding; neither the Keycloak theme nor password policies were patched here.
The focused browser helper retains its strict zero-error assertion.

Earlier bounded failures are retained, not counted as passes:

1. The initially selected test subnet overlapped an existing Docker network.
   It was changed to independently checked a non-overlapping private test subnet; no existing network changed.
2. The reused Alpine backend lacked Bash. Its isolated probe was corrected to
   curl; readiness then passed with the original timeout unchanged.
3. Two early registrations hit the standalone helper's default 5-second URL
   assertion. The helper now uses explicit 30-second navigation and the existing
   repository's 15-second assertion budget, with no automatic retries.
4. One cold authenticated onboarding request exceeded the existing 10-second BFF
   backend deadline; provisioning completed afterward. Application timeouts were
   not changed. Subsequent focused runs completed this flow. Cold-start latency
   remains a deployment sizing/acceptance concern, not a proven frontend fix.
5. An initial diagnostic's local Playwright import path was wrong and corrected.

## Keycloak: exact remaining security blockers

The official `quay.io/keycloak/keycloak:latest` still resolves to the already
pinned 26.8.1 index
`sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b`.
No unchanged Keycloak image was rebuilt or rescanned.

Both HIGH findings remain in
`java-21-openjdk-headless 1:21.0.12.1.1-1.2.el9`:

| Finding | Evidence and decision |
|---|---|
| [CVE-2026-91403](https://access.redhat.com/security/cve/cve-2026-91403) | Distrusted eMudhra certificate-chain issue. Red Hat documents updated OS CA trust as a sufficient mitigation for nonportable RPM JDKs. Existing CA package is `2026.2.92_v9.0.319-90.3.el9_8`. This is vendor mitigation evidence, **not an approved scanner exception**; deployed trust-store verification and review remain required. |
| [CVE-2026-91415](https://access.redhat.com/security/cve/cve-2026-91415) | OpenJDK 2D crafted graphics input can cause memory-safety/availability and data impacts. No exposed graphics processing path was established, but absence of a font-upload feature does not prove unreachability. Remains blocked pending patched runtime or separately reviewed evidence. |

A fresh bounded UBI9 query on 26 October returned **the same JDK and CA package
versions**, with exit 0. The current Temurin Jammy/Noble Java21 moving images also
still report **21.0.12.1_1**; the explicit `21.0.13_9-jre-noble` tag was not found.
The official [Temurin21.0.13+9 binary release](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.13%2B9)
does exist. Its Linux x64 JRE download matched upstream SHA256
`a535a1df9c6284d9618872984e026ae0352d1f7e9e0605425c0e6114f47351dd`.
Thus **it would be inaccurate to claim that no patched Java21 binary exists**.

Keycloak [supports OpenJDK21](https://www.keycloak.org/server/supported-configurations),
but that does not certify an arbitrary replacement of the vendor container's
JDK, trust-store integration and OS libraries. No custom JRE swap was forced or
adopted. A future checksum-pinned custom build needs its own full image inventory,
Java advisory coverage, CA/SMTP checks and all OIDC/registration/recovery/invitation
flows. This checkpoint's auth tests use the **unchanged** Keycloak image; they
are not compatibility evidence for that proposed Java replacement.

## Commands and scope

Commands executed with the scoped Node24 binary where `node` is shown:

```sh
docker buildx imagetools inspect gcr.io/distroless/nodejs24-debian13:nonroot
docker buildx imagetools inspect quay.io/keycloak/keycloak:latest
docker buildx imagetools inspect eclipse-temurin:21-jre-jammy
docker buildx imagetools inspect eclipse-temurin:21-jre-noble
docker buildx imagetools inspect eclipse-temurin:21.0.13_9-jre-noble
docker buildx build --platform linux/amd64 --load --progress plain -f infra/production/frontend.Dockerfile -t workforce-frontend:distroless-candidate .
docker image save -o .verification/phase13-runtime-options/frontend.tar workforce-frontend:distroless-candidate
trivy image --input .verification/phase13-runtime-options/frontend.tar --cache-dir .verification/phase13b1/trivy-cache --cache-backend memory --parallel 2 --timeout 4m --skip-db-update --skip-java-db-update --scanners vuln --format json --output .verification/phase13-runtime-options/frontend-scan.json
node infra/production/scan-verification.mjs .verification/phase13-runtime-options/frontend-scan.json
node --test infra/production/frontend-entrypoint.test.mjs infra/production/production.test.mjs infra/production/release.test.mjs infra/production/line-endings.test.mjs
node infra/production/frontend-browser-check.mjs
node --check infra/production/frontend-browser-check.mjs
actionlint .github/workflows/ci.yml
git diff --check
```

The ignored `compat.mjs` fixture helper recorded production-derived configuration,
immutable dependency IDs and bounded `docker compose ... up -d --no-build --wait
--wait-timeout 180`, plus permissions/health checks. The browser helper consumes
its `ports.json` and a scoped `FRONTEND_CHECK_BROWSER` executable; it does not
load or reuse original user credentials. Scan archives, fixture secrets, JRE
downloads and databases are not committed.

Docker/registry operations, official release downloads and Chromium execution
required outside-sandbox execution. Native Trivy, scoped Node unit checks and
repository checks ran locally. No global toolchain changed. Only the frontend
was built; build caches were reused. No GitHub run, publication or deployment
was performed.

Focused Node infrastructure/entrypoint/release/line-ending checks: **40/40 unique
tests passed** in one combined execution. Earlier overlapping runs are not added
to that total. Actionlint and whitespace checks passed. The browser helper's final
metadata change retains sanitized error information; it does not alter assertions
and was syntax-checked, not used to claim an additional browser pass.

## Handoff

- Frontend runtime image: strict scan PASS and requested functional paths
  exercised. The global browser console gate remains FAIL for the independently
  reproduced Keycloak issue above; this is not full application acceptance.
- Complete image set: **FAIL / NO-GO**, because Keycloak still has two HIGHs and
  the existing Mock SAP runtime inventory remains 1 CRITICAL/56 HIGH.
  Historical backend JRE advisory-coverage concerns and retained UNKNOWN findings
  in other images also still require review; earlier OS-only scans are not Java
  security clearance.
- Next useful work: a vendor-patched Keycloak/JDK image or a separately verified
  supported Java21 build, plus upstream registration-script remediation. Do not
  keep rebuilding unchanged official roots or approve blanket exceptions.
- After separately authorized push: exact-revision core CI and zero-retry E2E,
  then fresh scans of every assembled release image. This local checkpoint
  authorizes no push, publication, VPS purchase, merge or deployment.
