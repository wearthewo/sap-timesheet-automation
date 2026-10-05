CREATE TABLE timesheets (
    id uuid PRIMARY KEY,
    employee_id uuid NOT NULL REFERENCES employees(id),
    year integer NOT NULL,
    month integer NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    total_hours numeric(10,2) NOT NULL DEFAULT 0,
    submitted_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_timesheet_employee_month UNIQUE(employee_id, year, month),
    CONSTRAINT ck_timesheet_year CHECK (year BETWEEN 1 AND 9999),
    CONSTRAINT ck_timesheet_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_timesheet_total CHECK (total_hours >= 0),
    CONSTRAINT ck_timesheet_version CHECK (version >= 0),
    CONSTRAINT ck_timesheet_state CHECK (
        (status = 'DRAFT' AND submitted_at IS NULL) OR
        (status = 'SUBMITTED' AND submitted_at IS NOT NULL)
    )
);
