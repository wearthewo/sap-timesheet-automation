CREATE TABLE notifications (
 id UUID PRIMARY KEY,
 type VARCHAR(40) NOT NULL CHECK(type IN ('TIMESHEET_REMINDER','TIMESHEET_REJECTED','TIMESHEET_APPROVED','APPROVAL_REMINDER','SAP_EXPORT_FAILED','SAP_EXPORT_UNKNOWN','FINANCE_MONTHLY_REMINDER')),
 recipient_user_id UUID NOT NULL REFERENCES app_users(id),
 recipient_email VARCHAR(320) NOT NULL,
 subject VARCHAR(255) NOT NULL,
 body TEXT NOT NULL CHECK(length(body)<=30000),
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','PROCESSING','SENT','FAILED','CANCELLED')),
 deduplication_key VARCHAR(500) NOT NULL UNIQUE,
 attempt_count INTEGER NOT NULL DEFAULT 0 CHECK(attempt_count>=0),
 next_attempt_at TIMESTAMPTZ,
 sent_at TIMESTAMPTZ,
 business_year INTEGER,
 business_month INTEGER,
 CHECK((business_year IS NULL AND business_month IS NULL) OR (business_year BETWEEN 1900 AND 9999 AND business_month BETWEEN 1 AND 12)),
 CHECK(type NOT IN ('TIMESHEET_REMINDER','APPROVAL_REMINDER','FINANCE_MONTHLY_REMINDER') OR (business_year IS NOT NULL AND business_month IS NOT NULL)),
 last_error_code VARCHAR(80),
 last_error_message VARCHAR(500),
 locked_at TIMESTAMPTZ,
 locked_by VARCHAR(100),
 claim_token UUID,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK((status='SENT' AND sent_at IS NOT NULL) OR (status<>'SENT' AND sent_at IS NULL)),
 CHECK((status='PENDING' AND next_attempt_at IS NOT NULL) OR (status<>'PENDING' AND next_attempt_at IS NULL)),
 CHECK((status='PROCESSING' AND locked_at IS NOT NULL AND locked_by IS NOT NULL AND claim_token IS NOT NULL)
 OR (status<>'PROCESSING' AND locked_at IS NULL AND locked_by IS NULL AND claim_token IS NULL))
);
CREATE INDEX ix_notifications_due ON notifications(next_attempt_at,id) WHERE status='PENDING';
CREATE INDEX ix_notifications_lease ON notifications(locked_at,id) WHERE status='PROCESSING';
CREATE INDEX ix_notifications_recipient ON notifications(recipient_user_id,created_at DESC,id DESC);
