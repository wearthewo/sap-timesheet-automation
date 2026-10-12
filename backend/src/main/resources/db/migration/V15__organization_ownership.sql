-- Existing installations must explicitly attest that the pre-tenant database is one organization.
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM app_users) AND NOT ${legacyTenantConfirmed} THEN
  RAISE EXCEPTION 'Review legacy ownership and set LEGACY_TENANT_CONFIRMED=true before migrating this populated single-organization database';
 END IF;
END $$;
CREATE TABLE organizations (
 id uuid PRIMARY KEY, slug varchar(100) NOT NULL UNIQUE, name varchar(200) NOT NULL,
 status varchar(20) NOT NULL CHECK(status IN ('ACTIVE','SUSPENDED')),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK (btrim(slug)<>'' AND btrim(name)<>'')
);
INSERT INTO organizations(id,slug,name,status) VALUES ('00000000-0000-0000-0000-000000000001','legacy','Legacy organization','ACTIVE');
CREATE TABLE organization_memberships (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id), user_id uuid NOT NULL REFERENCES app_users(id),
 role varchar(20) NOT NULL CHECK(role IN ('EMPLOYEE','MANAGER','FINANCE','ADMIN')),
 status varchar(20) NOT NULL CHECK(status IN ('ACTIVE','SUSPENDED')),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(organization_id,user_id)
);
CREATE INDEX ix_membership_user ON organization_memberships(user_id,organization_id);
INSERT INTO organization_memberships(id,organization_id,user_id,role,status)
 SELECT gen_random_uuid(),'00000000-0000-0000-0000-000000000001',id,role,'ACTIVE' FROM app_users;
COMMENT ON COLUMN app_users.role IS 'Deprecated legacy value; authorization uses organization_memberships.role exclusively.';

-- ADD COLUMN with a constant backfills without mutating immutable history through UPDATE triggers.
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['employees','projects','project_assignments','timesheets','timesheet_entries','timesheet_approvals',
 'sap_timesheet_exports','sap_export_jobs','sap_export_attempts','notifications','notification_attempts'] LOOP
  EXECUTE format('ALTER TABLE %I ADD COLUMN organization_id uuid NOT NULL DEFAULT ''00000000-0000-0000-0000-000000000001'' REFERENCES organizations(id)',t);
  EXECUTE format('ALTER TABLE %I ALTER COLUMN organization_id DROP DEFAULT',t);
  EXECUTE format('ALTER TABLE %I ADD UNIQUE(id,organization_id)',t);
  EXECUTE format('CREATE INDEX ON %I(organization_id,id)',t);
 END LOOP;
END $$;
ALTER TABLE employees DROP CONSTRAINT uk_employee_user, DROP CONSTRAINT uk_employee_number;
ALTER TABLE employees ADD CONSTRAINT uk_employee_user UNIQUE(organization_id,user_id), ADD CONSTRAINT uk_employee_number UNIQUE(organization_id,employee_number);
ALTER TABLE projects DROP CONSTRAINT uk_project_code;
ALTER TABLE projects ADD CONSTRAINT uk_project_code UNIQUE(organization_id,code);
ALTER TABLE employees ADD FOREIGN KEY(organization_id,user_id) REFERENCES organization_memberships(organization_id,user_id);
ALTER TABLE employees ADD FOREIGN KEY(manager_id,organization_id) REFERENCES employees(id,organization_id);
ALTER TABLE project_assignments ADD FOREIGN KEY(employee_id,organization_id) REFERENCES employees(id,organization_id), ADD FOREIGN KEY(project_id,organization_id) REFERENCES projects(id,organization_id);
ALTER TABLE timesheets ADD FOREIGN KEY(employee_id,organization_id) REFERENCES employees(id,organization_id);
ALTER TABLE timesheet_entries ADD FOREIGN KEY(timesheet_id,organization_id) REFERENCES timesheets(id,organization_id), ADD FOREIGN KEY(project_id,organization_id) REFERENCES projects(id,organization_id);
ALTER TABLE timesheet_approvals ADD FOREIGN KEY(timesheet_id,organization_id) REFERENCES timesheets(id,organization_id), ADD FOREIGN KEY(manager_employee_id,organization_id) REFERENCES employees(id,organization_id);
ALTER TABLE sap_timesheet_exports ADD FOREIGN KEY(timesheet_id,organization_id) REFERENCES timesheets(id,organization_id);
ALTER TABLE sap_export_jobs ADD FOREIGN KEY(timesheet_id,organization_id) REFERENCES timesheets(id,organization_id), ADD FOREIGN KEY(export_id,organization_id) REFERENCES sap_timesheet_exports(id,organization_id), ADD UNIQUE(id,export_id,organization_id);
ALTER TABLE sap_export_attempts ADD FOREIGN KEY(job_id,export_id,organization_id) REFERENCES sap_export_jobs(id,export_id,organization_id);
ALTER TABLE notifications ADD FOREIGN KEY(organization_id,recipient_user_id) REFERENCES organization_memberships(organization_id,user_id);
ALTER TABLE notification_attempts ADD FOREIGN KEY(notification_id,organization_id) REFERENCES notifications(id,organization_id);
ALTER TABLE notifications DROP CONSTRAINT notifications_deduplication_key_key;
ALTER TABLE notifications ADD UNIQUE(organization_id,deduplication_key);
CREATE INDEX ix_notifications_org_recipient ON notifications(organization_id,recipient_user_id,created_at DESC,id DESC);
CREATE INDEX ix_timesheets_org_status ON timesheets(organization_id,status,year DESC,month DESC);

-- Children inherit ownership from their immutable parent; composite FKs check every other reference.
CREATE FUNCTION enforce_organization_ownership() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_org uuid;
BEGIN
 IF TG_OP='UPDATE' AND NEW.organization_id IS DISTINCT FROM OLD.organization_id THEN
  RAISE EXCEPTION 'Organization ownership is immutable' USING ERRCODE='23514';
 END IF;
 IF TG_NARGS=2 THEN
  EXECUTE format('SELECT organization_id FROM %I WHERE id=$1',TG_ARGV[0]) INTO parent_org
   USING (to_jsonb(NEW)->>TG_ARGV[1])::uuid;
  IF NEW.organization_id IS NULL THEN NEW.organization_id=parent_org; END IF;
  IF NEW.organization_id IS DISTINCT FROM parent_org THEN
   RAISE EXCEPTION 'Parent organization mismatch' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER organization_owner BEFORE INSERT OR UPDATE ON employees FOR EACH ROW EXECUTE FUNCTION enforce_organization_ownership();
CREATE TRIGGER organization_owner BEFORE INSERT OR UPDATE ON projects FOR EACH ROW EXECUTE FUNCTION enforce_organization_ownership();
CREATE TRIGGER organization_owner BEFORE INSERT OR UPDATE ON notifications FOR EACH ROW EXECUTE FUNCTION enforce_organization_ownership();
CREATE TRIGGER organization_owner BEFORE INSERT OR UPDATE ON project_assignments FOR EACH ROW EXECUTE FUNCTION enforce_organization_ownership('employees','employee_id');
CREATE TRIGGER organization_owner BEFORE INSERT OR UPDATE ON timesheets FOR EACH ROW EXECUTE FUNCTION enforce_organization_ownership('employees','employee_id');
CREATE TRIGGER organization_owner BEFORE INSERT OR UPDATE ON timesheet_entries FOR EACH ROW EXECUTE FUNCTION enforce_organization_ownership('timesheets','timesheet_id');
CREATE TRIGGER organization_owner BEFORE INSERT OR UPDATE ON timesheet_approvals FOR EACH ROW EXECUTE FUNCTION enforce_organization_ownership('timesheets','timesheet_id');
CREATE TRIGGER organization_owner BEFORE INSERT OR UPDATE ON sap_timesheet_exports FOR EACH ROW EXECUTE FUNCTION enforce_organization_ownership('timesheets','timesheet_id');
CREATE TRIGGER organization_owner BEFORE INSERT OR UPDATE ON sap_export_jobs FOR EACH ROW EXECUTE FUNCTION enforce_organization_ownership('sap_timesheet_exports','export_id');
CREATE TRIGGER organization_owner BEFORE INSERT OR UPDATE ON sap_export_attempts FOR EACH ROW EXECUTE FUNCTION enforce_organization_ownership('sap_export_jobs','job_id');
CREATE TRIGGER organization_owner BEFORE INSERT OR UPDATE ON notification_attempts FOR EACH ROW EXECUTE FUNCTION enforce_organization_ownership('notifications','notification_id');
