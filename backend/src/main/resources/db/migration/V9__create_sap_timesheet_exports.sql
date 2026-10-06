CREATE TABLE sap_timesheet_exports (
 id UUID PRIMARY KEY,
 timesheet_id UUID NOT NULL REFERENCES timesheets(id),
 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','SUCCEEDED','FAILED','UNKNOWN')),
 external_reference VARCHAR(128),
 failure_code VARCHAR(80),
 failure_message VARCHAR(500),
 exported_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uq_sap_export_timesheet UNIQUE(timesheet_id),
 CONSTRAINT ck_sap_export_result CHECK (
  (status = 'SUCCEEDED' AND external_reference IS NOT NULL AND exported_at IS NOT NULL AND failure_code IS NULL AND failure_message IS NULL)
  OR (status = 'PENDING' AND external_reference IS NULL AND exported_at IS NULL AND failure_code IS NULL AND failure_message IS NULL)
  OR (status IN ('FAILED','UNKNOWN') AND external_reference IS NULL AND exported_at IS NULL AND failure_code IS NOT NULL AND failure_message IS NOT NULL)
 )
);
CREATE UNIQUE INDEX uq_sap_external_reference ON sap_timesheet_exports(external_reference) WHERE external_reference IS NOT NULL;
CREATE INDEX ix_sap_exports_status ON sap_timesheet_exports(status);
