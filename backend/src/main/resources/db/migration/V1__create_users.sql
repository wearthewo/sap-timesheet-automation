CREATE TABLE app_users (
 id uuid PRIMARY KEY, external_subject varchar(255) NOT NULL,
 email varchar(320) NOT NULL, display_name varchar(255) NOT NULL,
 role varchar(20) NOT NULL DEFAULT 'EMPLOYEE',
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT uk_user_subject UNIQUE (external_subject),
 CONSTRAINT ck_user_role CHECK (role IN ('EMPLOYEE','MANAGER','FINANCE','ADMIN'))
);
CREATE INDEX ix_user_email ON app_users(email);

