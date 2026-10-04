CREATE TABLE project_assignments (
 id uuid PRIMARY KEY, employee_id uuid NOT NULL REFERENCES employees(id),
 project_id uuid NOT NULL REFERENCES projects(id),
 valid_from date NOT NULL, valid_to date, active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT ck_assignment_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE UNIQUE INDEX uk_active_assignment ON project_assignments(employee_id, project_id) WHERE active;
CREATE INDEX ix_assignment_project ON project_assignments(project_id);
CREATE INDEX ix_assignment_employee ON project_assignments(employee_id);

