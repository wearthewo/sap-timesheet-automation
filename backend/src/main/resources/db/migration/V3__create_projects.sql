CREATE TABLE projects (
 id uuid PRIMARY KEY, code varchar(50) NOT NULL, name varchar(200) NOT NULL,
 description varchar(2000), active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT uk_project_code UNIQUE(code),
 CONSTRAINT ck_project_code CHECK (btrim(code) <> ''),
 CONSTRAINT ck_project_name CHECK (btrim(name) <> '')
);

