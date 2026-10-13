-- Legacy accounts, employee identifiers and history remain unchanged.
ALTER TABLE employees ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE employees ADD COLUMN display_name varchar(255);
ALTER TABLE employees ADD CONSTRAINT ck_employee_display_name CHECK(display_name IS NULL OR btrim(display_name)<>'');
ALTER TABLE employees ADD COLUMN version bigint NOT NULL DEFAULT 0 CHECK(version>=0);
ALTER TABLE organization_memberships ADD COLUMN version bigint NOT NULL DEFAULT 0 CHECK(version>=0);
ALTER TABLE organization_memberships DROP CONSTRAINT organization_memberships_status_check;
ALTER TABLE organization_memberships ADD CONSTRAINT organization_memberships_status_check CHECK(status IN ('ACTIVE','SUSPENDED','REVOKED'));

CREATE TABLE organization_invitations (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id),
 employee_id uuid, email varchar(320) NOT NULL CHECK(email=lower(btrim(email))),
 role varchar(20) NOT NULL CHECK(role IN ('EMPLOYEE','MANAGER','FINANCE','ADMIN')),
 token_hash varchar(64) NOT NULL UNIQUE CHECK(length(token_hash)=64),
 status varchar(20) NOT NULL CHECK(status IN ('PENDING','ACCEPTED','REVOKED','EXPIRED')),
 expires_at timestamptz NOT NULL, created_by uuid NOT NULL REFERENCES app_users(id),
 accepted_by uuid REFERENCES app_users(id), accepted_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(id,organization_id), FOREIGN KEY(employee_id,organization_id) REFERENCES employees(id,organization_id),
 FOREIGN KEY(organization_id,created_by) REFERENCES organization_memberships(organization_id,user_id),
 CHECK(expires_at>created_at),
 CHECK((status='ACCEPTED' AND accepted_by IS NOT NULL AND accepted_at IS NOT NULL) OR
       (status<>'ACCEPTED' AND accepted_by IS NULL AND accepted_at IS NULL))
);
CREATE UNIQUE INDEX uq_pending_invitation_email ON organization_invitations(organization_id,email) WHERE status='PENDING';
CREATE UNIQUE INDEX uq_pending_invitation_employee ON organization_invitations(employee_id) WHERE status='PENDING' AND employee_id IS NOT NULL;
CREATE INDEX ix_invitation_org_created ON organization_invitations(organization_id,created_at DESC,id);
CREATE TRIGGER organization_owner BEFORE INSERT OR UPDATE ON organization_invitations FOR EACH ROW EXECUTE FUNCTION enforce_organization_ownership();

CREATE TABLE organization_admin_audit (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id), actor_user_id uuid NOT NULL REFERENCES app_users(id),
 action varchar(80) NOT NULL, resource_id uuid NOT NULL, details jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_admin_audit_org ON organization_admin_audit(organization_id,created_at DESC,id);
CREATE TRIGGER admin_audit_immutable BEFORE UPDATE OR DELETE ON organization_admin_audit FOR EACH ROW EXECUTE FUNCTION reject_notification_attempt_mutation();

-- Locks are scoped to one organization. API writes take this lock before loading mutable rows.
CREATE FUNCTION protect_last_organization_admin() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.organization_id IS DISTINCT FROM OLD.organization_id OR NEW.user_id IS DISTINCT FROM OLD.user_id THEN
  RAISE EXCEPTION 'Membership ownership is immutable' USING ERRCODE='23514';
 END IF;
 IF OLD.status='ACTIVE' AND OLD.role='ADMIN' AND (NEW.status<>'ACTIVE' OR NEW.role<>'ADMIN') THEN
  PERFORM id FROM organizations WHERE id=OLD.organization_id FOR UPDATE;
  IF NOT EXISTS(SELECT 1 FROM organization_memberships WHERE organization_id=OLD.organization_id AND id<>OLD.id AND role='ADMIN' AND status='ACTIVE') THEN
   RAISE EXCEPTION 'LAST_ACTIVE_ADMIN' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER membership_admin_guard BEFORE UPDATE ON organization_memberships FOR EACH ROW EXECUTE FUNCTION protect_last_organization_admin();
CREATE FUNCTION protect_employee_manager() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND OLD.user_id IS NOT NULL AND NEW.user_id IS DISTINCT FROM OLD.user_id THEN
  RAISE EXCEPTION 'Employee account linkage is immutable' USING ERRCODE='23514';
 END IF;
 IF NEW.manager_id IS NOT NULL THEN
  PERFORM id FROM organizations WHERE id=NEW.organization_id FOR UPDATE;
  IF EXISTS(WITH RECURSIVE chain(id,manager_id) AS (
   SELECT id,manager_id FROM employees WHERE id=NEW.manager_id AND organization_id=NEW.organization_id
   UNION SELECT e.id,e.manager_id FROM employees e JOIN chain c ON e.id=c.manager_id WHERE e.organization_id=NEW.organization_id
  ) SELECT 1 FROM chain WHERE id=NEW.id) OR NEW.manager_id=NEW.id THEN
   RAISE EXCEPTION 'MANAGER_CYCLE' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER employee_manager_guard BEFORE INSERT OR UPDATE ON employees FOR EACH ROW EXECUTE FUNCTION protect_employee_manager();

-- Invitations may target a person with no application identity or membership yet.
-- The body is a safe template; secret acceptance links are constructed only at delivery.
ALTER TABLE notifications ALTER COLUMN recipient_user_id DROP NOT NULL;
ALTER TABLE notifications ADD COLUMN invitation_id uuid;
ALTER TABLE notifications ADD FOREIGN KEY(invitation_id,organization_id) REFERENCES organization_invitations(id,organization_id);
ALTER TABLE notifications ADD CONSTRAINT ck_notification_recipient CHECK(
 (type='ORGANIZATION_INVITATION' AND invitation_id IS NOT NULL AND recipient_user_id IS NULL) OR
 (type<>'ORGANIZATION_INVITATION' AND invitation_id IS NULL AND recipient_user_id IS NOT NULL));
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK(type IN ('TIMESHEET_REMINDER','TIMESHEET_REJECTED','TIMESHEET_APPROVED','APPROVAL_REMINDER','SAP_EXPORT_FAILED','SAP_EXPORT_UNKNOWN','FINANCE_MONTHLY_REMINDER','ORGANIZATION_INVITATION'));
