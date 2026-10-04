CREATE TABLE employees (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_users(id),
 employee_number varchar(100) NOT NULL, active boolean NOT NULL DEFAULT true,
 manager_id uuid REFERENCES employees(id),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT uk_employee_user UNIQUE(user_id),
 CONSTRAINT uk_employee_number UNIQUE(employee_number),
 CONSTRAINT ck_employee_number CHECK (btrim(employee_number) <> ''),
 CONSTRAINT ck_employee_manager CHECK (manager_id IS NULL OR manager_id <> id)
);
CREATE INDEX ix_employee_manager ON employees(manager_id);

