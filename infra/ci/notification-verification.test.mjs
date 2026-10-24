import { test } from 'node:test';
import assert from 'node:assert/strict';
import { notificationReadiness, notificationDiagnostics, waitForNotifications } from './notification-verification.mjs';
import { verifyMailpit } from '../dev/verify-phase8-mailpit.mjs';

const manager = '00000000-0000-0000-0000-000000000001';
function cancelled() {
  return { id:'cancelled-digest',organization_id:'organization-a',type:'APPROVAL_REMINDER',status:'CANCELLED',
    recipient_email:'manager@example.test',subject:'Pending reviews',body:'Review for Νίκος & André\n',
    created_at:'2026-10-24T05:00:00Z',business_year:2026,business_month:10,
    deduplication_key:`APPROVAL_REMINDER:${manager}:2026-10:2026-10-24`,sent_at:null,attempt_count:1,
    cancellation_evidence:{ organization_status:'ACTIVE',membership_status:'ACTIVE',role:'MANAGER',manager_id:manager,manager_active:true,
      attempts:[{attempt_number:1,started_at:'2026-10-24T05:00:05Z',finished_at:'2026-10-24T05:00:05Z',outcome:'CANCELLED',safe_error_code:'NOTIFICATION_NO_LONGER_REQUIRED'}],
      timesheets:[{id:'reviewed-sheet',status:'APPROVED',submitted_at:'2026-10-24T04:59:00Z',decisions:[
        {id:'decision',manager_employee_id:manager,decision:'APPROVED',decided_at:'2026-10-24T05:00:03Z'}]}] } };
}
const sentTypes = ['TIMESHEET_REMINDER','APPROVAL_REMINDER','TIMESHEET_REJECTED','TIMESHEET_APPROVED','SAP_EXPORT_FAILED','SAP_EXPORT_UNKNOWN','FINANCE_MONTHLY_REMINDER'];
function delivered() { return sentTypes.map(type => ({id:type,type,status:'SENT',recipient_email:'employee@example.test',subject:type,body:'Νίκος & André\nCorrect link: http://localhost/app\n'})); }
const sheet = n => n.cancellation_evidence.timesheets[0];
const attempt = n => n.cancellation_evidence.attempts[0];

for (const decision of ['APPROVED','REJECTED']) test(`approval digest cancellation independently supported by ${decision} decision`, () => {
  const n=cancelled(); sheet(n).status=decision; sheet(n).decisions[0].decision=decision;
  assert.equal(notificationReadiness([n,...delivered()]),true);
});
test('rejection cancellation retains evidence across later reopen/resubmit/approval', () => {
  const n=cancelled(); sheet(n).decisions[0].decision='REJECTED';
  sheet(n).submitted_at='2026-10-24T05:01:00Z';
  sheet(n).decisions.push({id:'later',manager_employee_id:manager,decision:'APPROVED',decided_at:'2026-10-24T05:02:00Z'});
  assert.equal(notificationReadiness([n]),true);
});
test('business-key day uses Europe/Athens at UTC midnight boundary', () => {
  const n=cancelled(); n.created_at='2026-10-23T22:00:00Z';
  assert.equal(notificationReadiness([n]),true);
});

for (const [label, change] of [
  ['missing business evidence',n=>delete n.cancellation_evidence],
  ['unsupported cancellation type',n=>n.type='TIMESHEET_REMINDER'],
  ['missing business key',n=>delete n.deduplication_key],
  ['wrong period',n=>n.business_month=9],
  ['wrong manager key',n=>n.deduplication_key=n.deduplication_key.replace(manager,'00000000-0000-0000-0000-000000000002')],
  ['inactive organization',n=>n.cancellation_evidence.organization_status='SUSPENDED'],
  ['inactive membership',n=>n.cancellation_evidence.membership_status='SUSPENDED'],
  ['lost role is not silently allowed',n=>n.cancellation_evidence.role='EMPLOYEE'],
  ['inactive manager',n=>n.cancellation_evidence.manager_active=false],
  ['unknown cancellation reason',n=>attempt(n).safe_error_code='OTHER'],
  ['missing attempt history',n=>n.cancellation_evidence.attempts=[]],
  ['ambiguous previous attempts',n=>n.attempt_count=2],
  ['sent attempt disguised as cancellation',n=>attempt(n).outcome='SENT'],
  ['already delivered',n=>n.sent_at='2026-10-24T05:00:04Z'],
  ['no associated timesheet',n=>n.cancellation_evidence.timesheets=[]],
  ['still pending',n=>sheet(n).status='PENDING_APPROVAL'],
  ['another direct report still pending',n=>n.cancellation_evidence.timesheets.push({...sheet(n),id:'second',status:'PENDING_APPROVAL'})],
  ['missing decision',n=>sheet(n).decisions=[]],
  ['future decision cannot justify cancellation',n=>sheet(n).decisions[0].decided_at='2026-10-24T05:00:06Z'],
  ['old decision predating enqueue',n=>sheet(n).decisions[0].decided_at='2026-10-24T04:59:59Z'],
  ['another manager decision',n=>sheet(n).decisions[0].manager_employee_id='different-manager'],
  ['resubmitted before cancellation',n=>sheet(n).submitted_at='2026-10-24T05:00:04Z'],
]) test(`notification gate rejects ${label}`, () => {
  const n=cancelled(); change(n); assert.throws(()=>notificationReadiness([n]));
});

test('FAILED and unknown states fail immediately, even while other deliveries are pending', async () => {
  for(const status of ['FAILED','UNKNOWN','CORRUPT']) {
    let calls=0;
    await assert.rejects(waitForNotifications(()=>{calls++;return [{id:'waiting',status:'PENDING'},{id:'bad',status}];}, {sleep:()=>assert.fail('must not hide failures by waiting')}),/Notification bad ended/);
    assert.equal(calls,1);
  }
});
test('invalid cancellation fails immediately rather than becoming a readiness timeout', async () => {
  const n=cancelled(); sheet(n).status='PENDING_APPROVAL';
  await assert.rejects(waitForNotifications(()=>[n],{sleep:()=>assert.fail('must not retry invalid evidence')}),/Unexpected cancellation/);
});
test('PENDING and PROCESSING must reach SENT; original deadline remains 60 seconds', async () => {
  for(const status of ['PENDING','PROCESSING']) {
    let clock=0;
    await assert.rejects(waitForNotifications(()=>[{id:'waiting',status}],{now:()=>clock,sleep:async ms=>{clock+=ms;}}),new RegExp(`Notification delivery deadline exceeded: waiting:${status}`));
    assert.equal(clock,60000);
  }
  let reads=0;
  const rows=await waitForNotifications(()=>[{id:'progress',status:++reads===1?'PROCESSING':'SENT'}],{sleep:async()=>{}});
  assert.equal(rows[0].status,'SENT'); assert.equal(reads,2);
});
test('empty snapshot cannot pass',()=>assert.throws(()=>notificationReadiness([]),/empty/));
test('diagnostics preserve evidence without recipient, content or credentials',()=>{
  const n=cancelled();n.token='secret-token';
  const [safe]=notificationDiagnostics([n]);
  assert.equal(safe.deduplication_key,n.deduplication_key);
  assert.deepEqual(safe.cancellation_evidence,n.cancellation_evidence);
  for(const value of [n.recipient_email,n.subject,n.body,n.token]) assert.ok(!JSON.stringify(safe).includes(value));
});

function mailbox(intents, mutate=()=>{}) {
  const messages=intents.filter(n=>n.status==='SENT').map(n=>({ID:n.id,Subject:n.subject,Text:n.body,HTML:'',To:[{Address:n.recipient_email}]}));
  mutate(messages);
  return async url=>{
    const parsed=new URL(url);
    if(parsed.pathname==='/api/v1/search') {
      const query=parsed.searchParams.get('query');
      // Search can return a wrong-recipient result; verification must check To itself.
      return Response.json({messages:messages.filter(m=>query.endsWith(`subject:"${m.Subject}"`)).map(m=>({ID:m.ID}))});
    }
    const message=messages.find(m=>m.ID===parsed.pathname.split('/').at(-1));
    return message?Response.json(message):new Response(null,{status:404});
  };
}
test('Mailpit verifies every SENT intent and proves supported cancellation was not delivered', async()=>{
  const intents=[...delivered(),cancelled()],logs=[];
  await verifyMailpit(intents,{request:mailbox(intents),log:m=>logs.push(m)});
  assert.equal(logs.filter(m=>/^PASS [A-Z_]+:/.test(m)).length,7);
  assert.ok(logs.some(m=>m.startsWith('PASS total: 7;')));
  assert.ok(logs.some(m=>m.startsWith('PASS cancellations: 1;')));
});
for(const [label,mutate] of [
  ['missing delivery',m=>m.shift()],
  ['duplicate delivery',m=>m.push({...m[0],ID:'duplicate'})],
  ['wrong recipient',m=>m[0].To=[{Address:'unrelated@example.test'}]],
  ['wrong content',m=>m[0].Text='Altered body'],
  ['HTML instead of plain text',m=>m[0].HTML='<p>Changed</p>'],
]) test(`Mailpit still rejects ${label}`,async()=>{
  const intents=delivered(); await assert.rejects(verifyMailpit(intents,{request:mailbox(intents,mutate),log:()=>{}}));
});
test('Mailpit rejects physical delivery of a cancelled notification',async()=>{
  const n=cancelled(),intents=[...delivered(),n];
  const request=mailbox(intents,m=>m.push({ID:n.id,Subject:n.subject,Text:n.body,HTML:'',To:[{Address:n.recipient_email}]}));
  await assert.rejects(verifyMailpit(intents,{request,log:()=>{}}),/Cancelled notification was delivered/);
});
test('cancellation cannot replace required SENT business-type coverage',async()=>{
  const intents=[...delivered().filter(n=>n.type!=='APPROVAL_REMINDER'),cancelled()];
  await assert.rejects(verifyMailpit(intents,{request:mailbox(intents),log:()=>{}}),/Missing business notification type APPROVAL_REMINDER/);
});
test('Mailpit never accepts unresolved or unsupported cancelled intents',async()=>{
  for(const status of ['PENDING','PROCESSING','FAILED']) {
    await assert.rejects(verifyMailpit([...delivered(),{id:'bad',status}],{request:()=>assert.fail('must validate first'),log:()=>{}}));
  }
});
