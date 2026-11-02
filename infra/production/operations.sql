-- Aggregate operational state only; no bodies, tokens, addresses or comments.
SELECT version,success FROM flyway_schema_history ORDER BY installed_rank;
SELECT status,count(*),min(next_attempt_at) next_due FROM sap_export_jobs GROUP BY status;
SELECT status,count(*) FROM sap_timesheet_exports GROUP BY status;
SELECT status,count(*),min(next_attempt_at) next_due FROM notifications GROUP BY status;
SELECT count(*) expired_sap_claims FROM sap_export_jobs WHERE status='PROCESSING' AND locked_at < now()-interval '5 minutes';
SELECT count(*) expired_notification_claims FROM notifications WHERE status='PROCESSING' AND locked_at < now()-interval '5 minutes';
SELECT pg_size_pretty(pg_database_size('workforce')) workforce_size,pg_size_pretty(pg_database_size('keycloak')) keycloak_size;
