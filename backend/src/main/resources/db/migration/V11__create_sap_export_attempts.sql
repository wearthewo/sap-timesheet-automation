CREATE TABLE sap_export_attempts(
 id UUID PRIMARY KEY,
 export_id UUID NOT NULL REFERENCES sap_timesheet_exports(id),
 job_id UUID NOT NULL REFERENCES sap_export_jobs(id),
 attempt_number INTEGER NOT NULL CHECK(attempt_number>0),
 operation VARCHAR(20) NOT NULL CHECK(operation IN ('EXPORT','RECONCILE')),
 started_at TIMESTAMPTZ NOT NULL,
 finished_at TIMESTAMPTZ NOT NULL,
 outcome VARCHAR(20) NOT NULL CHECK(outcome IN ('SUCCEEDED','FAILED','UNKNOWN','NOT_FOUND','ABANDONED')),
 safe_error_code VARCHAR(80),
 duration_ms BIGINT NOT NULL CHECK(duration_ms>=0),
 external_reference VARCHAR(128),
 UNIQUE(job_id,attempt_number),
 CHECK(finished_at>=started_at)
);
CREATE INDEX ix_sap_attempts_export ON sap_export_attempts(export_id,attempt_number DESC);
CREATE FUNCTION reject_sap_attempt_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'SAP attempt history is immutable' USING ERRCODE='23514'; END; $$;
CREATE TRIGGER sap_attempts_immutable BEFORE UPDATE OR DELETE ON sap_export_attempts FOR EACH ROW EXECUTE FUNCTION reject_sap_attempt_mutation();
