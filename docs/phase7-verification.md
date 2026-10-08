# Phase 7 verification

Verification date: 2026-10-08. Local Docker Desktop only; no deployment or production provider.

## Scope and changed files

- notification/api: NotificationController, NotificationDtos.
- notification/application: NotificationIntents, NotificationEvaluation, NotificationTemplates, NotificationPolicy, NotificationWorker, NotificationHistory.
- notification/domain: EmailGateway, EmailMessage, EmailDeliveryResult, EmailDeliveryException, NotificationType, NotificationStatus.
- notification/infrastructure: NotificationStore, SmtpEmailGateway, NotificationClockConfiguration, NotificationScheduling.
- ApprovalService and SapJobStore create notification intent in existing transactions.
- New migrations: V12__create_notifications.sql, V13__create_notification_attempts.sql. V1–V11 unchanged.
- New dependency: managed spring-boot-starter-mail.
- Frontend: /app/notifications and authenticated navigation, smoke test and two Playwright workflows.
- Configuration: application.yaml, .env.example, compose.yaml (local Mailpit).
- Documentation: notifications.md, architecture.md, README and permanent engineering rules in AGENTS.md.

APIs: GET /api/v1/notifications and GET /api/v1/notifications/{id}; authenticated owner-only. OpenAPI generated from controllers.

See [notifications.md](notifications.md) for event/periodic rules, deduplication keys, database claims/leases, retries, timezone, transaction boundaries, SMTP delivery limitations and configuration.

## Commands and results

Final results: all passed, with zero skipped tests.

- ./mvnw test: 235 tests, zero failures/errors/skips. This includes all 182 previous tests, 43 new PostgreSQL integration cases and 10 SMTP cases.
- ./mvnw clean package -DskipTests: succeeded (Java 21 executable Spring Boot artifact).
- pnpm test:browser: 10 complete workflows passed (8 existing plus 2 notification/Mailpit workflows), 6.9 minutes.
- Java 21 compile succeeded.
- SMTP integration: 10 tests passed against a deterministic socket server and safe failure stubs.
- pnpm install --frozen-lockfile succeeded.
- pnpm lint, pnpm typecheck and pnpm build succeeded; lint/typecheck rerun after browser-test changes passed.
- pnpm test:smoke: 17 passed.
- mock SAP: node --test, 7 passed.
- docker compose config --quiet succeeded.
- docker compose up --build -d succeeded with six healthy services.
- Backend health, frontend health, OpenAPI, Swagger UI, Mailpit readyz and UI returned HTTP 200.
- Both new notification browser workflows and all earlier-phase browser workflows passed.

Backend commands: ./mvnw test and ./mvnw clean package -DskipTests.
Browser command: pnpm test:browser, including earlier Phase 1–6 workflows.

The required Java 21 and Node 24 runtimes were project-scoped Docker containers outside the Codex sandbox. Maven used the repository wrapper and a named dependency cache. Frontend used pnpm 12.8.1 and named dependency/build volumes. Compose validation itself ran in the normal sandbox; image pulls, builds, container runs, Testcontainers/Docker socket access, fixture preparation and browser execution used outside-sandbox execution. No global toolchains were changed.

## Browser/Mailpit scenarios

Fresh local Keycloak EMPLOYEE, MANAGER, FINANCE and ADMIN accounts were provisioned, with database roles, active employee/manager profiles and date-valid assignments. Credentials remain only in ignored .verification/phase7-users.json.

A temporary Compose environment shortened notification evaluation to five seconds, delivery to two seconds and current-month reminder day to one. Default configuration was restored after verification (PT15M evaluation, PT5S delivery, day 28 employee reminders). Tests poll for observable delivery; no Thread.sleep-based scheduler tests are used.

The browser tests cover missing/current draft reminder, repeat evaluation deduplication, direct-report manager digest, rejection with UTF-8/script-like plain text, explicit employee reopen/edit/resubmit, approval email, terminal SAP validation failure, UNKNOWN reconciliation-required alerts to Finance/Admin, owner-only history, mobile layout and logout. Mailpit's UI was opened and a delivered rejection was inspected; API assertions verify exact recipient, subject, safe text and application links. Screenshots are local ignored test-results artifacts.

Backend tests directly invoke evaluation with an injected Clock, covering short-month clamping and JVM timezone independence. They also verify ownership, concurrent scheduler deduplication, SKIP LOCKED claiming, future due dates, lease recovery, stale-token fencing, immutable history, atomic result rollback and absence of a transaction across SMTP.

## Issues found and fixed

- MIME copying an empty message failed before SMTP. The adapter now populates Spring's created message directly and supplies the stable Message-ID; SMTP integration tests pass.
- Unsupported HTTP methods were mapped to 500 by the existing generic advice. They now return the conventional 405 error DTO with Allow.
- SMTP health probing would couple service availability to Mailpit. Mail health/test-connection are disabled; delivery failures remain durable worker state.
- Queued reminders could become irrelevant before delivery. The claim checks current eligibility and records CANCELLED without SMTP.
- New SQL fixtures omitted required SAP failure fields/timestamps; fixed to honor the existing schema.
- The new browser test captured an approval-list URL before navigation finished; it now waits for review content.
- The initial browser run hit a Keycloak login timeout after an interrupted/long-running session; clean fixtures and a fresh complete run are used for final results.
- PowerShell treated pnpm's informational stderr as a terminating native-command error. The ignored verification runner now respects the actual process exit code.

## Deliberate limits

No generic send API, read/unread state, manual notification retry, admin operations screen, automatic attachments or extra channels. SMTP is bounded at-least-once under ambiguous acceptance, not exactly-once delivery. No production SMTP/SAP credentials, broker, payroll, deployment or Phase 8.
