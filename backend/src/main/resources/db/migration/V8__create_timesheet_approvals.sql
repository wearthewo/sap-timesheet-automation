CREATE TABLE timesheet_approvals (
 id uuid PRIMARY KEY,
 timesheet_id uuid NOT NULL REFERENCES timesheets(id),
 manager_employee_id uuid NOT NULL REFERENCES employees(id),
 decision varchar(20) NOT NULL CHECK (decision IN ('APPROVED','REJECTED')),
 comment varchar(2000),
 decided_at timestamptz NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT ck_rejection_comment CHECK (decision <> 'REJECTED' OR (comment IS NOT NULL AND comment ~ '[^[:space:]]'))
);
CREATE INDEX ix_approvals_timesheet ON timesheet_approvals(timesheet_id,decided_at,id);
CREATE INDEX ix_approvals_manager ON timesheet_approvals(manager_employee_id);
CREATE FUNCTION prevent_approval_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Approval history is immutable' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER immutable_approval_history BEFORE UPDATE OR DELETE ON timesheet_approvals
 FOR EACH ROW EXECUTE FUNCTION prevent_approval_mutation();
