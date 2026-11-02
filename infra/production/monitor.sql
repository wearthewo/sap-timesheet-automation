-- Operator-only aggregate health snapshot. Never select recipients, tokens or payloads.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SELECT json_build_object(
 'sapFailed', (SELECT count(*) FROM sap_timesheet_exports WHERE status='FAILED'),
 'sapUnknown', (SELECT count(*) FROM sap_timesheet_exports WHERE status='UNKNOWN'),
 'notificationFailed', (SELECT count(*) FROM notifications WHERE status='FAILED'),
 'sapOverdue', (SELECT count(*) FROM sap_export_jobs WHERE status='PENDING' AND next_attempt_at < now()-interval '15 minutes'),
 'notificationOverdue', (SELECT count(*) FROM notifications WHERE status='PENDING' AND next_attempt_at < now()-interval '15 minutes'),
 'sapExpiredLeases', (SELECT count(*) FROM sap_export_jobs WHERE status='PROCESSING' AND (locked_at IS NULL OR locked_at < now()-interval '5 minutes')),
 'notificationExpiredLeases', (SELECT count(*) FROM notifications WHERE status='PROCESSING' AND (locked_at IS NULL OR locked_at < now()-interval '5 minutes')),
 'migrationFailures', (SELECT count(*) FROM flyway_schema_history WHERE NOT success)
);
COMMIT;
