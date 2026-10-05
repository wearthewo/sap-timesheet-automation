CREATE TABLE timesheet_entries (
    id uuid PRIMARY KEY,
    timesheet_id uuid NOT NULL REFERENCES timesheets(id),
    project_id uuid NOT NULL REFERENCES projects(id),
    work_date date NOT NULL,
    hours numeric(7,2) NOT NULL,
    description varchar(2000),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_entry_hours CHECK (hours > 0)
);
CREATE INDEX ix_entry_timesheet_date ON timesheet_entries(timesheet_id, work_date, id);
CREATE INDEX ix_entry_project ON timesheet_entries(project_id);
