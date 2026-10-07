# Repository rules

Phase 6 includes identity, employees, projects, assignments, employee timesheets, manager decisions and durable asynchronous SAP export through a local HTTP mock, plus Finance reporting and CSV/XLSX downloads.
Use Java 21, Node 24, pnpm and strict TypeScript. Never downgrade toolchains to match the host; use scoped runtimes or containers.
PostgreSQL only. Schema changes require Flyway; Hibernate validates only. Use Testcontainers for database integration tests.
Preserve feature/domain-oriented packages. APIs use DTOs; never expose JPA entities.
Authentication must not be bypassed. Authorization is enforced server-side using application database roles.
Use constructor injection, clear transactions, Bean Validation and database constraints. Avoid unnecessary abstractions.
No microservices unless explicitly requested; no Kafka unless explicitly justified; no Kubernetes.
No secrets committed or logged. Never log JWTs, passwords or access tokens.
Next.js is a Node-runtime BFF. Spring Boot owns business logic and persistence. No Vercel-specific functionality.
Do not implement production SAP connectivity, messaging, notifications or payroll in this phase. Do not deploy.

Timesheets and entries extend TimestampedEntity; do not duplicate inherited fields. Resolve self-service ownership from the authenticated application user. Every entry mutation and submission must check the client timesheet version, update the aggregate version, and maintain totals in one transaction. Pending and approved timesheets are read-only for employees. Rejected timesheets require explicit versioned reopening before editing. Manager decisions require the database MANAGER role, an active employee profile, direct-report ownership, and no self-review. Decision rows are append-only; status, history, and version must commit atomically. Use BigDecimal/decimal storage for hours; never floating-point arithmetic.

SAP-specific payloads and transport details stay in the sap infrastructure adapter. Reserve export identity in a short committed transaction before outbound HTTP; never hold a database transaction across SAP calls. Preserve one stable identity per timesheet, prevent duplicate logical exports, and retain ambiguous outcomes for reconciliation. Do not log upstream bodies or integration credentials.

PostgreSQL owns durable SAP jobs. Claims use SKIP LOCKED, bounded leases and a fenced claim token; never hold locks across HTTP. UNKNOWN or expired claims must reconcile before resending. Retries preserve the payload snapshot and stable identity, have capped backoff and bounded attempts, and append immutable history atomically with export/job results.

Reporting reads authoritative tables through bounded projections; no N+1 entity loading or copied reporting tables. Only FINANCE/ADMIN may access reports/downloads. Derived reporting states never enter operational enums. Approved-hour datasets require APPROVED; SAP-exported mode additionally requires SUCCEEDED. Preserve decimal arithmetic and historical project entries. Buffer bounded snapshots and close DB transactions before file formatting.
