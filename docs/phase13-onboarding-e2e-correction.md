# Phase 13 onboarding hydration correction

26–27 October 2026 (Europe/Athens), `feature/phase12-production-readiness`. Investigated parent
`b32750a71b0aa113ccafda01e707ffd019b350eb`, GitHub run
[40368862742](https://github.com/wearthewo/sap-timesheet-automation/actions/runs/40368862742),
job `121733864999`. No push, publication, deployment or security exception approval.

## Failed-run evidence

The downloaded JUnit report records **16 browser tests, 15 passed, 1 failed,
0 skipped**. Smoke passed 28/28 and all six services started. The only browser
failure was `onboarding.spec.ts:53`, waiting for Main navigation after the
collision-owner account tried to create `taken-<random suffix>`.

The failure's `error-context.md` shows the onboarding heading, no active
memberships, an **empty Organization name**, the populated `taken-...` address,
and the normal Create organization button. No slug-conflict or service error
was visible. This setup creates its own unique collision target; it does not
depend on a seeded organization or another spec. The second account's deliberate
duplicate-slug checks had not yet run.

The uploaded archive contains this page snapshot and service diagnostics, but
**no onboarding screenshot, trace or HAR**. The repository intentionally disables
automatic traces/screenshots because identity flows contain passwords and email
action tokens. Other specs' screenshots do not describe this failure. Consequently
the GitHub request sequence and database state cannot be reconstructed directly
from that artifact; they are not claimed as observed network/database facts.

## Deterministic real-browser reproduction

Used the existing disposable `workforce-frontend-check-3c8d75c654` fixture with
fresh tmpfs PostgreSQL and synthetic Keycloak registrations/Mailpit. No original
container, database, realm or volume was modified.

With the unchanged production frontend image:

1. Register and verify a synthetic account, then open onboarding.
2. Hold Next.js scripts, reload the server-rendered form, and type the name.
3. Release scripts; after hydration, edit Organization address.
4. The name changes from `Collision fixture` to an empty string.
5. Click Create: native required-field validation says **Please fill out this field**.
6. URL remains `/onboarding`; sanitized request observation records **zero POSTs**.

This reproduces the exact empty-name symptom in the GitHub snapshot. There is no
organization creation request in the reproduction, so no organization is created
and no slug-conflict handling or session navigation is reached by that click.
The root cause is independent controlled input state initialized to empty strings:
typing before hydration does not update that state, and the later slug update
renders the stale empty name over the browser's existing text.

Local evidence (ignored, not committed):

- `.verification/phase13-e2e-40368862742/artifacts/`: original downloaded artifacts.
- `hydration-reproduction.json`: before/after values, safe URL path, native validity
  message and empty POST list; no cookies, tokens or request bodies.
- `hydration-reproduction.png`: empty required name and native validation message.
- `baseline-results/browser.xml`: strengthened test fails at the name-preservation
  assertion against the unchanged image (**1 expected failure**, 24.1 seconds).

## Correction

`create-form.tsx` retains controlled inputs and server-returned error values, but
synchronizes both name and slug from the live form on either edit and on submit.
This captures text typed before React attached its handlers instead of rendering
an empty state value over it. Controlled fields continue to protect subsequent
corrections from React's automatic action reset. No-JavaScript submission and
server error handling remain supported. No backend, session, authorization,
idempotency, slug-validation or timeout changes.

An intermediate uncontrolled-input/keyed-form candidate was rejected: it fixed
the early name loss, but the existing regression caught a corrected address being
reset to the taken address. It is not the final implementation. Its first browser
attempt also encountered a separate backend-unavailable response; read-only SQL
confirmed that its synthetic organization had committed. These failures remain in
`corrected-results/` and `corrected-confirmation-results/`, rather than being counted
as passing runs or hidden by retries. The initial availability failure is not
established as a hydration or slug-validation defect.

`onboarding.spec.ts` now deliberately types the collision owner's name while
scripts are held, releases them, waits for the normal page load, edits the address,
and asserts the name is retained before creating the organization. There are no
sleep calls, retries or removed assertions. Existing pre-hydration duplicate-slug,
hydrated duplicate-slug, corrected-slug, registration, isolation, mobile logout
and recovery assertions remain intact.

## Focused verification

The final controlled-field correction passed the complete focused onboarding
scenario: **1/1 test, 0 failures, 0 skips, 0 retries**, one worker. Test duration
**61.352 seconds**, total invocation **69.332 seconds**. JUnit is retained at
`.verification/phase13-e2e-40368862742/final-results/browser.xml`.

That one scenario includes the forced hydration boundary, creation of the isolated
collision target, a second new registration/email verification, duplicate-slug
errors both without scripts and after hydration, retained submitted values,
correction to an available slug, ADMIN dashboard/empty-workspace isolation,
session-cookie/token privacy checks, organization selection, mobile logout and
password-recovery email/reset. Assertions are not counted as separate tests.

The final production frontend image is
`sha256:6613d2e0a548ea18dac9952ced692195ee24e1cd6c8e95fe29bc801f22080d97`.
Production build, targeted ESLint, TypeScript and `git diff --check` passed.
The initial negative regression and two rejected intermediate-candidate runs
remain separately documented; they are not aggregated into a passing suite.

Exact focused commands (Node resolves to the repository-scoped 24.22.1 runtime):

```sh
# Repository root; only this frontend image was built/rebuilt.
docker build --platform linux/amd64 -f infra/production/frontend.Dockerfile \
  -t workforce-frontend:onboarding-correction .
node frontend/node_modules/@playwright/test/cli.js test \
  --config frontend/.verification/onboarding-ci.config.ts onboarding.spec.ts --workers=1
# From frontend/:
node node_modules/eslint/bin/eslint.js src/app/onboarding/create-form.tsx tests/browser/onboarding.spec.ts
node node_modules/typescript/bin/tsc --noEmit
# Repository root:
git diff --check
```

The ignored local Playwright config inherits repository assertions/timeouts and
zero retries, selects the existing Chromium binary, and maps the two synthetic
HTTPS hosts to the isolated proxy. `MAILPIT_API_URL` points only to that fixture.
Only its frontend was replaced during retesting. The fixture's tmpfs database
and synthetic identities are separate from existing application data. Docker,
browser and Git index/commit operations used outside-sandbox execution.

Files included in the correction:

- `frontend/src/app/onboarding/create-form.tsx`
- `frontend/tests/browser/onboarding.spec.ts`
- `docs/phase13-onboarding-e2e-correction.md`

**Ready for another exact-revision GitHub CI/E2E run after an authorized push.**
The full remote suite has not run on the new correction commit, and no new full
backend/smoke/security suite is claimed. The separate initial backend-availability
failure remains disclosed; its precise timing cause was not proven or concealed
by changing request budgets. No unrelated source, security gate, image pin,
migration or deployment configuration changed. No security clearance is implied.
