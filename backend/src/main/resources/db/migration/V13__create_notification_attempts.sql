CREATE TABLE notification_attempts (
 id UUID PRIMARY KEY,
 notification_id UUID NOT NULL REFERENCES notifications(id),
 attempt_number INTEGER NOT NULL CHECK(attempt_number>0),
 started_at TIMESTAMPTZ NOT NULL,
 finished_at TIMESTAMPTZ NOT NULL,
 outcome VARCHAR(20) NOT NULL CHECK(outcome IN ('SENT','RETRY_SCHEDULED','FAILED','CANCELLED')),
 safe_error_code VARCHAR(80),
 duration_ms BIGINT NOT NULL CHECK(duration_ms>=0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(notification_id,attempt_number),
 CHECK(finished_at>=started_at)
);
CREATE FUNCTION reject_notification_attempt_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Notification attempt history is immutable' USING ERRCODE='23514'; END; $$;
CREATE TRIGGER notification_attempts_immutable BEFORE UPDATE OR DELETE ON notification_attempts
 FOR EACH ROW EXECUTE FUNCTION reject_notification_attempt_mutation();
