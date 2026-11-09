# Portfolio capture and verification

**Status: ready for local owner review.** Captured November 8–9, 2026. Nothing was pushed, merged, deployed, uploaded or published; LinkedIn was not accessed. This is a showcase verification record, not a new production-security clearance or a complete regression-suite result.

## Provenance and scope

- Source baseline: `0552de97b82b6dfe9226ed7dd0f099c3f68689d0`, branch `docs/workforce-portfolio-showcase`.
- Actual browser target: the existing Phase 12F application at `http://localhost:33000`, with local Mailpit at `http://localhost:38025`.
- A separate **Aster Studio** organization and four synthetic identities were created through registration, email verification and invitations. No pre-existing business records were overwritten. The continuation reused these records rather than recreating them.
- Alex Morgan (ADMIN), Evelyn Stone (EMPLOYEE), Maya Chen (MANAGER) and Jordan Blake (FINANCE) are fictional demonstration identities. Visible `example.test` addresses are synthetic.
- Screenshots are genuine, unretouched Chromium captures. The video uses genuine recordings with title cards, captions, fades and editorial pacing. It is not a latency benchmark. Authentication forms, credentials, verification links and invitation tokens are excluded from the footage.
- SAP is the existing external HTTP **Mock SAP simulator**. Email uses the local Mailpit sink. Neither production SAP connectivity nor public hosting is claimed.

## Executed showcase workflows

These are **12 grouped browser workflows**, not a count of individual assertions or screenshots. All 12 completed successfully during the showcase work. Existing Phase 12F acceptance and earlier role/security checks were reused rather than rerunning a complete suite.

| ID | Workflow actually executed | Observed result | Status / evidence |
| --- | --- | --- | --- |
| S01 | Register and verify four new synthetic identities through Keycloak and Mailpit | Each role authenticated with its own account | PASS; private capture log, role screenshots |
| S02 | ADMIN creates the isolated organization | Empty workspace and initial ADMIN access | PASS; [empty overview](screenshots/admin-empty-overview.png) |
| S03 | Invite EMPLOYEE, MANAGER and FINANCE; recipients accept | Four active memberships with the intended roles | PASS; [members](screenshots/admin-members.png) |
| S04 | Create/link profiles, assign the employee to the manager, create three projects and assignments | Linked manager and employee; assigned projects available | PASS; [employees](screenshots/admin-employees.png), [assignments](screenshots/admin-assignments.png) |
| S05 | EMPLOYEE adds 5.50 and 2.50 hours and submits November | Total 8.00; PENDING_APPROVAL and read-only | PASS; [draft](screenshots/employee-draft.png), [submitted](screenshots/employee-submitted.png) |
| S06 | MANAGER opens the direct-report queue, reviews and approves | APPROVED with decision history; employee sees approved record | PASS; [queue](screenshots/manager-pending.png), [history](screenshots/manager-decision-history.png) |
| S07 | FINANCE queues November export and waits for the worker | QUEUED then SUCCEEDED, external reference and one export attempt | PASS; [queued](screenshots/finance-queued.png), [attempt](screenshots/finance-attempt-history.png) |
| S08 | Submit/approve a separate October 1.25-hour simulator exercise; export times out after persistence; FINANCE reconciles | UNKNOWN then SUCCEEDED; one export attempt followed by lookup, not a second create | PASS; [UNKNOWN](screenshots/finance-reconciliation-required.png), [reconciled](screenshots/finance-reconciled.png) |
| S09 | FINANCE downloads SAP XLSX, reporting CSV and reporting XLSX | Three actual downloads; workbooks valid; CSV decimal hours sum to 8.00 | PASS; local download inspection described below |
| S10 | Inspect monthly reporting and employee/finance notification history | November shows 8.00 approved/exported hours; approval and UNKNOWN notification histories visible | PASS; [report](screenshots/finance-report.png), [employee notifications](screenshots/employee-notifications.png), [finance notifications](screenshots/finance-notifications.png) |
| S11 | Navigate as all four roles at 390 × 844 | Role-appropriate navigation and loaded dashboards | PASS; [gallery](README.md#notifications-and-mobile) |
| S12 | Log out as all four roles, including Keycloak confirmation | Local session and SSO logout completed | PASS; private browser results; no credential footage |

The video presents S04–S07 and S10. The additional reconciliation exercise is documented in screenshots; it is not falsely represented as a video chapter. November has two active employee profiles, so the manager's missing personal timesheet appears in reporting; the eight recorded hours belong to Evelyn. The later manager screenshot correctly shows an empty pending queue after both decisions.

## Deliverables and media checks

| Artifact | Result |
| --- | --- |
| [MP4](video/workforce-demo.mp4) | **109.833333 seconds**, 1920 × 1080, H.264 High / `yuv420p`, 30 fps, 3,295 frames, 3,120,642 bytes (2.98 MiB); no audio track |
| Full-file decode | `ffmpeg -v error -i docs/portfolio/video/workforce-demo.mp4 -f null -` completed with exit 0 and no decoding errors |
| Browser playback | Chromium played to `ended`, correct dimensions/duration, no media error; accelerated 4× verification reported 84 dropped presentation frames out of 3,295. This is not a zero-drop performance claim; the full decode independently passed |
| Visual video review | Twelve representative frames covering every chapter were inspected in a storyboard, with selected full-resolution frame checks; captions and simulator labeling checked |
| Screenshots | **26 PNGs**, all decoded and visually inspected, including four mobile views. Loading-state mobile captures were replaced; history captures now include the complete rows |
| Video preview | Genuine application frame, JPEG decoded and reviewed; links to the MP4 |
| Diagrams | **6 Mermaid sources + 6 SVGs**; Mermaid 11.4.1 parse/render passed, standalone SVG browser-image decode passed, all six visually inspected. SVG XML serialization corrected and intrinsic dimensions retained |
| README architecture | Inline Mermaid parsed and rendered successfully |
| Documentation | Local Markdown preview inspected and all five local README images loaded; external badge requests were deliberately not fetched. Local Markdown/HTML file links checked. Original README content preserved in [development-guide.md](../development-guide.md), with documentation-relative links and an explicit historical-context preface |
| Downloads | SAP XLSX: 2 sheets; reporting XLSX: 5 sheets. Both valid ZIP/XML workbooks, numeric cells and eight-hour values present, no formula cells. CSV: header + 2 rows, 17 columns, both projects and decimal total 8.00 |

MP4 SHA-256:

```text
0ff535fbaaa798b39c0ff692c7f74b0af95380a7e4c71008137e7dd241a0aaed
```

The MP4 is small enough to include in a reviewed normal Git commit. No Git LFS or external media hosting was introduced. GitHub may open a file view rather than autoplay; the download link remains available. No GitHub-hosted rendering or playback is claimed before publication.

## Source versus running images

| Component | Actual capture environment |
| --- | --- |
| Frontend | Phase 12F image `sha256:e6326259263d0e1955f22b545f69bc43968438a5544e9118246d7c965e62cc0f`; Node 24.22.1, Next.js 16.4.1, React 19.3.0 |
| Backend | Phase 12F image `sha256:939e08ce224961b0e71fc2bb1215b5fd3704d69219c5f9f81cce5220664b1fcb`; retained acceptance JAR matches running JAR SHA-256 `8243a994c823ff806f4874c8bb1d8a47e73989dcb7a8fc5738093aa4f20cb101` |
| Keycloak | Existing local 26.8.1 image, not the newer custom production security candidate |
| Mock SAP | Existing Node 24.22.0 image; running application source hash matches current source |

The current source includes the later organization-name hydration correction (`frontend/src/app/onboarding/create-form.tsx` and its browser regression). The running frontend predates that correction. Backend application source matches the Phase 12F baseline. Production runtime hardening, including the custom patched Keycloak and minimal runtime images, is not represented by this local stack. None of these differences was hidden by rebuilding the environment.

## Privacy, environment and Git safety

- Every final screenshot was visually checked. Only synthetic business data is shown. No password entry, invitation/verification URL, access token, real customer information or private key was found in the reviewed media.
- Portfolio text/assets, README and the preserved guide were checked against the exact private fixture passwords/tokens in memory and for private-key/GitHub-token/JWT patterns. No matches were found. PNG/JPEG EXIF checks passed; SVGs contain no executable script elements or event-handler attributes. This is a scoped media review, not a new entire-history secret audit.
- Private credentials, original recordings, downloaded workbooks, harnesses and detailed machine evidence remain under ignored `.verification/portfolio-20261108/`. They are excluded from the proposed portfolio change.
- Final read-only Docker inspection found **all six original containers healthy**, with their original creation/start timestamps and images. The PostgreSQL, Mock SAP and Mailpit named data volumes remain mounted. No rebuild, restart, recreation, deletion, database reset or volume operation was performed.
- Final disk check: **41,290,833,920 bytes free (38.46 GiB)**. The retained raw/edit evidence uses additional local space; no cleanup was performed.
- The proposed change contains **46 files**: README, the preserved development guide, four portfolio Markdown documents, 26 PNG screenshots, six Mermaid sources, six SVG renders, one JPEG preview and one MP4. Only README and documentation/media are part of this change. Existing unrelated untracked Phase 13 reports and `frontend/test.png` were left untouched and must not be staged with the showcase. No application source changes or commits were made.

## Limitations and next step

This task did not rerun the entire backend/Playwright/security suite, production load tests, physical mobile devices, Safari/Firefox, real SAP or external SMTP. Registration and invitations worked in the exercised hydrated-page path; that does not remove the documented running-image hydration difference. Mailpit registration/invitation messages were used, but a new aggregate exact-mail acceptance suite was not run for this media task.

Capture harness corrections included selectors, waiting for loaded dashboard content, and a browser playback-quality serialization mistake; these were corrected without application changes. A failed early employee take was excluded from the edit and retained privately. Standalone SVG export was repaired after XML validation caught unclosed HTML breaks.

**Next step:** owner reviews [README](../../README.md), [gallery](README.md), [video](video/workforce-demo.mp4) and the optional [LinkedIn Featured text](linkedin-featured.md). Commit/push/publication remain separate owner decisions. No blocking media defect remains from the checks above.
