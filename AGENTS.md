# Repository rules

Phase 2 includes identity, employees, projects, assignments and employee monthly timesheets.
Use Java 21, Node 24, pnpm and strict TypeScript. Never downgrade toolchains to match the host; use scoped runtimes or containers.
PostgreSQL only. Schema changes require Flyway; Hibernate validates only. Use Testcontainers for database integration tests.
Preserve feature/domain-oriented packages. APIs use DTOs; never expose JPA entities.
Authentication must not be bypassed. Authorization is enforced server-side using application database roles.
Use constructor injection, clear transactions, Bean Validation and database constraints. Avoid unnecessary abstractions.
No microservices unless explicitly requested; no Kafka unless explicitly justified; no Kubernetes.
No secrets committed or logged. Never log JWTs, passwords or access tokens.
Next.js is a Node-runtime BFF. Spring Boot owns business logic and persistence. No Vercel-specific functionality.
Do not implement approvals, reporting, SAP integration, workers, messaging, notifications or payroll in this phase. Do not deploy.

Timesheets and entries extend TimestampedEntity; do not duplicate inherited fields. Resolve self-service ownership from the authenticated application user. Every entry mutation and submission must check the client timesheet version, update the aggregate version, and maintain totals in one transaction. Submitted timesheets are immutable. Use BigDecimal/decimal storage for hours; never floating-point arithmetic.
