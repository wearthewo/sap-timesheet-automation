ALTER TABLE sap_timesheet_exports DROP CONSTRAINT sap_timesheet_exports_status_check;
ALTER TABLE sap_timesheet_exports DROP CONSTRAINT ck_sap_export_result;
UPDATE sap_timesheet_exports SET status='UNKNOWN', failure_code='SAP_LEGACY_PENDING',
 failure_message='Previous export outcome is unconfirmed. Reconciliation is required.', updated_at=now() WHERE status='PENDING';
ALTER TABLE sap_timesheet_exports ADD CONSTRAINT ck_sap_export_status CHECK(status IN ('QUEUED','PROCESSING','SUCCEEDED','FAILED','UNKNOWN'));
ALTER TABLE sap_timesheet_exports ADD CONSTRAINT ck_sap_export_result CHECK(
 (status='SUCCEEDED' AND external_reference IS NOT NULL AND exported_at IS NOT NULL AND failure_code IS NULL AND failure_message IS NULL) OR
 (status IN ('QUEUED','PROCESSING','FAILED','UNKNOWN') AND external_reference IS NULL AND exported_at IS NULL
  AND ((failure_code IS NULL AND failure_message IS NULL AND status IN ('QUEUED','PROCESSING')) OR (failure_code IS NOT NULL AND failure_message IS NOT NULL))));
ALTER TABLE sap_timesheet_exports ADD CONSTRAINT uq_sap_export_id_timesheet UNIQUE(id,timesheet_id);
CREATE TABLE sap_export_jobs(
 id UUID PRIMARY KEY,
 export_id UUID NOT NULL UNIQUE,
 timesheet_id UUID NOT NULL UNIQUE REFERENCES timesheets(id),
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
 operation VARCHAR(20) NOT NULL CHECK(operation IN ('EXPORT','RECONCILE')),
 payload JSONB NOT NULL,
 attempt_count INTEGER NOT NULL DEFAULT 0 CHECK(attempt_count>=0),
 cycle_attempts INTEGER NOT NULL DEFAULT 0 CHECK(cycle_attempts>=0 AND cycle_attempts<=attempt_count),
 reconciliation_count INTEGER NOT NULL DEFAULT 0 CHECK(reconciliation_count>=0),
 execution_count INTEGER NOT NULL DEFAULT 0 CHECK(execution_count>=attempt_count),
 next_attempt_at TIMESTAMPTZ NOT NULL,
 locked_at TIMESTAMPTZ,
 locked_by VARCHAR(100),
 claim_token UUID,
 last_attempt_at TIMESTAMPTZ,
 last_error_code VARCHAR(80),
 retry_eligible BOOLEAN NOT NULL DEFAULT false,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(export_id,timesheet_id) REFERENCES sap_timesheet_exports(id,timesheet_id),
 CHECK((status='PROCESSING' AND locked_at IS NOT NULL AND locked_by IS NOT NULL AND claim_token IS NOT NULL)
   OR (status<>'PROCESSING' AND locked_at IS NULL AND locked_by IS NULL AND claim_token IS NULL))
);
CREATE INDEX ix_sap_jobs_pending ON sap_export_jobs(next_attempt_at,id) WHERE status='PENDING';
CREATE INDEX ix_sap_jobs_lease ON sap_export_jobs(locked_at) WHERE status='PROCESSING';
INSERT INTO sap_export_jobs(id,export_id,timesheet_id,status,operation,payload,next_attempt_at,last_error_code)
SELECT gen_random_uuid(),x.id,t.id,
 CASE WHEN x.status='SUCCEEDED' THEN 'COMPLETED' WHEN x.status='UNKNOWN' THEN 'PENDING' ELSE 'FAILED' END,
 CASE WHEN x.status='UNKNOWN' THEN 'RECONCILE' ELSE 'EXPORT' END,
 jsonb_build_object('timesheetId',t.id,'employeeNumber',e.employee_number,'year',t.year,'month',t.month,'entries',
  COALESCE((SELECT jsonb_agg(jsonb_build_object('projectCode',p.code,'workDate',a.work_date,'hours',a.hours,'description',a.description) ORDER BY a.work_date,a.id)
  FROM timesheet_entries a JOIN projects p ON p.id=a.project_id WHERE a.timesheet_id=t.id),'[]'::jsonb)),now(),x.failure_code
FROM sap_timesheet_exports x JOIN timesheets t ON t.id=x.timesheet_id JOIN employees e ON e.id=t.employee_id;
