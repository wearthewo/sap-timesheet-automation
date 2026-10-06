ALTER TABLE timesheets DROP CONSTRAINT ck_timesheet_state;
-- Existing submissions retain their submission timestamp and enter manager review.
UPDATE timesheets SET status='PENDING_APPROVAL', version=version+1, updated_at=now() WHERE status='SUBMITTED';
ALTER TABLE timesheets ADD CONSTRAINT ck_timesheet_state CHECK (
 (status='DRAFT' AND submitted_at IS NULL) OR
 (status IN ('PENDING_APPROVAL','APPROVED','REJECTED') AND submitted_at IS NOT NULL)
);
CREATE INDEX ix_timesheets_employee_status ON timesheets(employee_id,status);
