# Email notifications (Phase 7)

The notification module evaluates business state, writes durable PostgreSQL intent, and delivers plain-text email through `EmailGateway`. `SmtpEmailGateway` is the only module that knows Spring Mail/Jakarta Mail/SMTP. The new dependency is Spring Boot's managed `spring-boot-starter-mail`. No real provider is configured.

## Events and reminders

Approval/rejection creates one employee notification per decision, within the same transaction as the decision and timesheet version change. Comments are plain text, including characters such as `<` and `>`; the history UI escapes them through React. Links use `APP_PUBLIC_BASE_URL`.

SAP terminal FAILED creates a Finance/Admin alert per export execution; transient retries do not generate FAILED alerts. UNKNOWN creates one alert per logical export lifetime, including lease recovery. Repeated reconciliation does not create repeated UNKNOWN emails. It explicitly asks for reconciliation, never a blind retry. The email describes the captured event; automatic reconciliation may have resolved it before it is read.

An injectable UTC Clock plus explicit `WORKFORCE_BUSINESS_TIMEZONE` determines business dates, independently of JVM timezone:

- Active employee profiles with a missing, DRAFT or REJECTED current-month timesheet receive at most one reminder per business day, on/after the configured day.
- PENDING_APPROVAL and APPROVED do not receive submission reminders.
- Day 31 clamps to February/month-end via `min(configuredDay, daysInMonth)`.
- Active MANAGER profiles with actual pending direct reports receive one digest per timesheet month/business day. It includes a bounded preview of 50 employees and the total pending count. Self-review is excluded.
- Finance/Admin receive one prior-month digest per month when incomplete timesheets or FAILED/UNKNOWN exports exist, on/after the configured Finance reminder day. No report attachments.

User email and roles come from the application database, never a browser recipient payload. The existing user model has no active flag: Finance/Admin recipients are provisioned application users, excluding those with an inactive employee profile; a profile is not required for these roles. Keycloak account disabling is not inferred through a new identity sync.

## Persistence and deduplication

V12 creates `notifications`, including recipient FK, checked type/state, unique deduplication key, retry/lease fields, optional reminder year/month and due/recipient indexes. JDBC persistence follows the existing durable SAP job pattern; no JPA entity is exposed. V13 creates immutable `notification_attempts` with a database UPDATE/DELETE guard.

Keys are:
- `TIMESHEET_REMINDER:<employeeId>:<year-month>:<businessDate>`
- `APPROVAL_REMINDER:<managerEmployeeId>:<year-month>:<businessDate>`
- `TIMESHEET_APPROVED|TIMESHEET_REJECTED:<decisionId>:<recipientUserId>`
- `SAP_EXPORT_FAILED:<exportId>:<executionCount>:<recipientUserId>`
- `SAP_EXPORT_UNKNOWN:<exportId>:<recipientUserId>`
- `FINANCE_MONTHLY_REMINDER:<year-month>:<recipientUserId>`

INSERT ... ON CONFLICT DO NOTHING and the unique constraint protect repeated/concurrent evaluators. There is no select-then-insert race.

States are PENDING → PROCESSING → SENT, or retryable PENDING / terminal FAILED. CANCELLED means the reminder is no longer relevant or the operational recipient lost their role. Relevance is checked in the claim transaction: submitted employee sheets, managers without matching pending reports, and removed Finance/Admin roles are cancelled before SMTP. Digest content is a creation-time snapshot; state can still change after the final database check. Avoiding that tiny race would require holding locks across SMTP, which is deliberately prohibited.

## Worker, retry and crash recovery

Workers select a bounded due batch using PostgreSQL FOR UPDATE SKIP LOCKED and persist PROCESSING, a unique claim token, owner, locked timestamp and incremented attempt count. SMTP calls run concurrently only within that bounded batch on Java 21 virtual threads. Multiple JVMs can safely share the queue.

A short result transaction locks the notification and checks the token before atomically appending history and changing state. A stale result cannot overwrite a newer claim. Lease expiration appends EMAIL_LEASE_EXPIRED history and schedules bounded retry, or becomes terminal FAILED at the limit. Recovery itself is bounded per poll.

Connection failures, timeouts, SMTP temporary 4xx and unconfirmed delivery are retryable. Invalid addresses/content, SMTP definitive 5xx and authentication failures are permanent. Safe application messages replace raw provider errors. Backoff is `min(initial × 2^(attempt-1), maximum)`: defaults 30s, 60s, 120s, with four total attempts and a 10-minute cap. No jitter is used, keeping tests deterministic.

SENT means SMTP accepted delivery; it does not prove inbox receipt or reading. SMTP is not an idempotent protocol. If SMTP accepts and the response or local result commit is lost, a retry can physically deliver twice. The stable Message-ID identifies the logical notification but cannot guarantee receiver deduplication. PostgreSQL prevents duplicate *intent*, while delivery is bounded at-least-once. This limitation is not disguised as exactly-once delivery.

## Transactions

1. Event: business transition + notification INSERT commit together; no SMTP.
2. Evaluation: read current state + deduplicated INSERTs; no SMTP.
3. Claim: SKIP LOCKED + PROCESSING/lease commit quickly.
4. Delivery: SMTP outside a database transaction and without database locks.
5. Result: append attempt + update notification commit atomically.

SMTP unavailability cannot roll back approval or SAP state. SMTP is excluded from application health, and startup does not test the SMTP connection. Queue state and safe delivery logs expose failures.

## APIs and UI

- GET /api/v1/notifications?page=0&size=20 — current user's paginated history, newest first, maximum size 100.
- GET /api/v1/notifications/{id} — owner only; another user's ID returns 404.

All roles see their own Notifications navigation/page at /app/notifications. Cards show subject, type, created UTC time, delivery status, sent UTC time and escaped plain-text message. There is no read/unread state, generic send endpoint, admin operations screen or manual retry endpoint in this phase.

## Local Mailpit and configuration

Run `docker compose up --build`. Mailpit v1.31.4 is the sixth service, with persistent local capture storage, health check, UI [localhost:8025](http://localhost:8025) and SMTP localhost:1025. Ports bind to loopback. The backend uses mailpit:1025; it does not depend on Mailpit health. Only local captured mail is sent.

| Variable | Default / meaning |
|---|---|
| APP_PUBLIC_BASE_URL | http://localhost:3000; links in email |
| WORKFORCE_BUSINESS_TIMEZONE | Europe/Athens |
| MAIL_HOST / MAIL_PORT | Compose mailpit / 1025; standalone localhost / 1025 |
| MAIL_USERNAME / MAIL_PASSWORD | empty locally |
| MAIL_FROM | noreply@workforce.local |
| MAIL_SMTP_AUTH / MAIL_STARTTLS_ENABLED | false locally; configurable SMTP capabilities |
| MAIL_CONNECT_TIMEOUT_MS / MAIL_READ_TIMEOUT_MS / MAIL_WRITE_TIMEOUT_MS | 3000 each; must be positive |
| MAILPIT_WEB_PORT / MAILPIT_SMTP_PORT | 8025 / 1025 |
| NOTIFICATION_SCHEDULER_ENABLED | false standalone, true in Compose |
| NOTIFICATION_EVALUATION_INTERVAL | PT15M |
| TIMESHEET_REMINDER_DAY_OF_MONTH | 28, clamped to month length |
| FINANCE_REMINDER_DAY_OF_MONTH | 5, previous month, clamped |
| NOTIFICATION_WORKER_ENABLED | false standalone, true in Compose |
| NOTIFICATION_BATCH_SIZE | 10, allowed 1–100 |
| NOTIFICATION_POLL_INTERVAL | PT5S |
| NOTIFICATION_PROCESSING_TIMEOUT | PT5M |
| NOTIFICATION_MAX_ATTEMPTS | 4, allowed 1–100 |
| NOTIFICATION_INITIAL_BACKOFF / NOTIFICATION_MAX_BACKOFF | PT30S / PT10M |

When delivery is enabled, the lease must exceed a conservative allowance of ten times the sum of SMTP timeouts plus ten seconds. Durations must be positive. For quick local reminder verification, temporarily set evaluation interval PT5S and reminder day 1, then recreate backend; restore defaults afterward. Deterministic automated tests invoke evaluation directly with an injected Clock, without waiting for scheduler intervals.

Logs include notification ID/type, recipient user ID, attempt, duration, outcome and safe code. They exclude addresses, message bodies, tokens, credentials, raw SMTP exchanges and exceptions. Persisted message bodies are owner-only application data; Mailpit is a local development capture tool and must not be publicly deployed as configured.

No other channels, automatic attachments, production provider credentials, broker, deployment or Phase 8 functionality is included.
Spring Mail implementation reference: [JavaMailSenderImpl](https://github.com/spring-projects/spring-framework/blob/v6.2.19/spring-context-support/src/main/java/org/springframework/mail/javamail/JavaMailSenderImpl.java) preserves an explicitly supplied Message-ID across saveChanges. Mailpit setup follows its [official Docker documentation](https://mailpit.axllent.org/docs/install/docker/).
