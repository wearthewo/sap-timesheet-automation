// Verification policy only. Never changes notification intent or delivery state.
import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';
import { sqlLiteral } from './support.mjs';

export function notificationSnapshotSql(subjects) {
  assert.ok(subjects.length > 0, 'Notification fixture subjects required');
  return `SELECT coalesce(json_agg(snapshot ORDER BY snapshot.id),'[]'::json) FROM (
    SELECT n.*, CASE WHEN n.status='CANCELLED' THEN json_build_object(
      'organization_status', o.status,
      'membership_status', membership.status, 'role', membership.role,
      'manager_id', manager.id, 'manager_active', manager.active,
      'attempts', (SELECT coalesce(json_agg(a ORDER BY a.attempt_number),'[]'::json) FROM (
        SELECT attempt_number,started_at,finished_at,outcome,safe_error_code
        FROM notification_attempts WHERE notification_id=n.id AND organization_id=n.organization_id
      ) a),
      'timesheets', (SELECT coalesce(json_agg(sheet ORDER BY sheet.id),'[]'::json) FROM (
        SELECT t.id,t.status,t.submitted_at,
          (SELECT coalesce(json_agg(review ORDER BY review.decided_at,review.id),'[]'::json) FROM (
            SELECT a.id,a.manager_employee_id,a.decision,a.decided_at FROM timesheet_approvals a
            WHERE a.timesheet_id=t.id AND a.organization_id=n.organization_id
          ) review) AS decisions
        FROM employees e JOIN timesheets t ON t.employee_id=e.id AND t.organization_id=e.organization_id
        WHERE e.organization_id=n.organization_id AND e.manager_id=manager.id AND e.id<>manager.id
          AND t.year=n.business_year AND t.month=n.business_month
      ) sheet)
    ) END AS cancellation_evidence
    FROM notifications n JOIN app_users u ON u.id=n.recipient_user_id
    JOIN organizations o ON o.id=n.organization_id
    LEFT JOIN organization_memberships membership ON membership.organization_id=n.organization_id AND membership.user_id=n.recipient_user_id
    LEFT JOIN employees manager ON manager.organization_id=n.organization_id AND manager.user_id=n.recipient_user_id
    WHERE u.external_subject IN (${subjects.map(sqlLiteral).join(',')})
  ) snapshot;`;
}

function instant(value) {
  assert.equal(typeof value, 'string', 'Missing notification evidence timestamp');
  const time = Date.parse(value);
  assert.ok(Number.isFinite(time), 'Invalid notification evidence timestamp');
  return time;
}

export function assertExpectedCancellation(n) {
  const fail = message => `Unexpected cancellation ${n.id}: ${message}`;
  // Only the observed obsolete manager digest is permitted. Other cases fail closed.
  assert.equal(n.type, 'APPROVAL_REMINDER', fail('unsupported notification type'));
  const e = n.cancellation_evidence;
  assert.ok(e, fail('missing independent business evidence'));
  assert.equal(e.organization_status, 'ACTIVE', fail('organization unavailable'));
  assert.equal(e.membership_status, 'ACTIVE', fail('membership unavailable'));
  assert.equal(e.role, 'MANAGER', fail('manager role missing'));
  assert.equal(e.manager_active, true, fail('manager profile inactive'));
  assert.match(e.manager_id ?? '', /^[a-f0-9-]{36}$/i, fail('manager identity missing'));
  assert.ok(Number.isInteger(n.business_year) && Number.isInteger(n.business_month) && n.business_month >= 1 && n.business_month <= 12, fail('invalid business period'));
  const created = instant(n.created_at);
  const day = new Intl.DateTimeFormat('en-CA', { timeZone:'Europe/Athens', year:'numeric', month:'2-digit', day:'2-digit' }).format(new Date(created));
  assert.equal(n.deduplication_key, `APPROVAL_REMINDER:${e.manager_id}:${n.business_year}-${String(n.business_month).padStart(2,'0')}:${day}`, fail('business key mismatch'));
  assert.equal(n.sent_at, null, fail('already sent'));
  assert.equal(n.attempt_count, 1, fail('ambiguous previous delivery attempts'));
  assert.equal(e.attempts?.length, 1, fail('missing or ambiguous attempt history'));
  const attempt = e.attempts[0];
  assert.equal(attempt.attempt_number, 1, fail('incorrect attempt number'));
  assert.equal(attempt.outcome, 'CANCELLED', fail('attempt outcome mismatch'));
  assert.equal(attempt.safe_error_code, 'NOTIFICATION_NO_LONGER_REQUIRED', fail('unrecognized cancellation reason'));
  const cancelled = instant(attempt.finished_at);
  assert.ok(created <= instant(attempt.started_at) && instant(attempt.started_at) <= cancelled, fail('inconsistent cancellation time'));
  assert.ok(Array.isArray(e.timesheets) && e.timesheets.length > 0, fail('no associated direct-report timesheets'));
  let resolvedAfterEnqueue = false;
  for (const sheet of e.timesheets) {
    assert.ok(['DRAFT','REJECTED','APPROVED'].includes(sheet.status), fail(`timesheet ${sheet.id} still pending or has invalid state`));
    assert.ok(Array.isArray(sheet.decisions), fail('missing approval history'));
    // A never-submitted draft is not part of an approval digest.
    if (sheet.status === 'DRAFT' && sheet.submitted_at === null && sheet.decisions.length === 0) continue;
    const prior = sheet.decisions.filter(d => instant(d.decided_at) <= cancelled).at(-1);
    assert.ok(prior && ['APPROVED','REJECTED'].includes(prior.decision), fail(`timesheet ${sheet.id} lacks a decision before cancellation`));
    assert.equal(prior.manager_employee_id, e.manager_id, fail('decision belongs to another manager'));
    // Do not infer obsolescence from a decision made before a later submission.
    if (sheet.submitted_at && instant(sheet.submitted_at) <= cancelled) {
      assert.ok(instant(prior.decided_at) >= instant(sheet.submitted_at), fail('timesheet was resubmitted before cancellation'));
    }
    if (instant(prior.decided_at) >= created) resolvedAfterEnqueue = true;
  }
  assert.ok(resolvedAfterEnqueue, fail('no review completed between enqueue and cancellation'));
}

export function notificationReadiness(rows) {
  assert.ok(Array.isArray(rows) && rows.length > 0, 'Notification verification snapshot is empty');
  let ready = true;
  for (const n of rows) {
    if (n.status === 'CANCELLED') assertExpectedCancellation(n);
    else if (n.status === 'PENDING' || n.status === 'PROCESSING') ready = false;
    else assert.equal(n.status, 'SENT', `Notification ${n.id} ended in ${n.status}`);
  }
  return ready;
}

// Keep the existing 60s deadline. Unlike general startup readiness, evidence failures
// must propagate immediately, not be swallowed until a generic timeout.
export async function waitForNotifications(readSnapshot, { timeoutMs=60000, intervalMs=1000, now=Date.now, sleep=delay } = {}) {
  const deadline = now() + timeoutMs;
  let rows;
  do {
    rows = await readSnapshot();
    if (notificationReadiness(rows)) return rows;
    await sleep(intervalMs);
  } while (now() < deadline);
  const unresolved = rows.filter(n => ['PENDING','PROCESSING'].includes(n.status)).map(n => `${n.id}:${n.status}`);
  throw new Error(`Notification delivery deadline exceeded: ${unresolved.join(', ')}`);
}

// Safe artifact: no email addresses, subjects, bodies, invitation links or tokens.
export function notificationDiagnostics(rows) {
  return rows.map(n => ({ id:n.id, organization_id:n.organization_id, type:n.type, status:n.status,
    deduplication_key:n.deduplication_key, business_year:n.business_year, business_month:n.business_month,
    created_at:n.created_at, sent_at:n.sent_at, attempt_count:n.attempt_count,
    cancellation_evidence:n.cancellation_evidence }));
}
