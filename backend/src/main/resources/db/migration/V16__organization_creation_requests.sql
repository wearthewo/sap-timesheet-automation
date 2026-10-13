-- Preserve legacy slugs; fail safely if an operator previously created normalized collisions.
CREATE UNIQUE INDEX uq_organization_normalized_slug ON organizations(lower(btrim(slug)));
CREATE TABLE organization_creation_requests (
 user_id uuid NOT NULL REFERENCES app_users(id),
 idempotency_key uuid NOT NULL,
 organization_id uuid NOT NULL UNIQUE REFERENCES organizations(id),
 requested_name varchar(200) NOT NULL,
 requested_slug varchar(63) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id,idempotency_key),
 FOREIGN KEY(organization_id,user_id) REFERENCES organization_memberships(organization_id,user_id),
 CHECK (btrim(requested_name) <> ''),
 CHECK (requested_slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND length(requested_slug)>=3)
);
