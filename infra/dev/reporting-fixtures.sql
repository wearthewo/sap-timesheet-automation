-- Explicit LOCAL development fixture, never a Flyway migration or automatic startup seed.
-- Run with psql -v organization_id=<uuid> -v report_prefix=REPORT6-<unique-suffix>. Seven employees, October 2028.
-- Synthetic SAP references below describe report fixtures, not real SAP documents.
\set ON_ERROR_STOP on
BEGIN;
INSERT INTO projects(organization_id,id,code,name,active) VALUES
(:'organization_id'::uuid,gen_random_uuid(),'REPORT-A','Reporting implementation',true),
(:'organization_id'::uuid,gen_random_uuid(),'REPORT-B','Reporting review',true) ON CONFLICT(organization_id,code) DO NOTHING;
INSERT INTO app_users(id,external_subject,email,display_name,role)
SELECT gen_random_uuid(),:'report_prefix'||'-'||letter,:'report_prefix'||'-'||letter||'@example.test','Report Employee '||letter,'EMPLOYEE'
FROM unnest(ARRAY['A','B','C','D','E','F','G']) letter;
INSERT INTO organization_memberships(id,organization_id,user_id,role,status)
SELECT gen_random_uuid(),:'organization_id'::uuid,id,'EMPLOYEE','ACTIVE' FROM app_users WHERE external_subject LIKE :'report_prefix'||'-%';
INSERT INTO employees(organization_id,id,user_id,employee_number,active)
SELECT :'organization_id'::uuid,gen_random_uuid(),id,external_subject,true FROM app_users WHERE external_subject LIKE :'report_prefix'||'-%';
INSERT INTO project_assignments(id,employee_id,project_id,valid_from,active)
SELECT gen_random_uuid(),e.id,p.id,'2028-01-01',true FROM employees e CROSS JOIN projects p
WHERE e.organization_id=:'organization_id'::uuid AND e.employee_number LIKE :'report_prefix'||'-%' AND p.organization_id=e.organization_id AND p.code IN ('REPORT-A','REPORT-B');
INSERT INTO timesheets(id,employee_id,year,month,status,total_hours,submitted_at,version)
SELECT gen_random_uuid(),e.id,2028,10,v.state,v.hours,CASE WHEN v.state='DRAFT' THEN NULL ELSE now() END,3
FROM (VALUES ('A','APPROVED',8.25),('B','APPROVED',4.50),('C','PENDING_APPROVAL',3.00),
('D','REJECTED',2.00),('E','DRAFT',1.25),('G','APPROVED',1.25)) v(letter,state,hours)
JOIN employees e ON e.organization_id=:'organization_id'::uuid AND e.employee_number=:'report_prefix'||'-'||v.letter;
INSERT INTO timesheet_entries(id,timesheet_id,project_id,work_date,hours,description)
SELECT gen_random_uuid(),t.id,p.id,'2028-10-05',v.hours,E'Worked on "billing, API"\nLocal report fixture'
FROM (VALUES ('A','REPORT-A',6.25),('A','REPORT-B',2.00),('B','REPORT-A',4.50),
('C','REPORT-A',3.00),('D','REPORT-A',2.00),('E','REPORT-A',1.25),('G','REPORT-B',1.25)) v(letter,code,hours)
JOIN employees e ON e.organization_id=:'organization_id'::uuid AND e.employee_number=:'report_prefix'||'-'||v.letter
JOIN timesheets t ON t.employee_id=e.id AND t.year=2028 AND t.month=10 JOIN projects p ON p.code=v.code AND p.organization_id=e.organization_id;
-- Use the existing local manager profile, without modifying relationships or previous history.
UPDATE employees e SET manager_id=(SELECT em.id FROM employees em JOIN organization_memberships m ON m.user_id=em.user_id AND m.organization_id=em.organization_id WHERE m.role='MANAGER' AND m.status='ACTIVE' AND em.organization_id=:'organization_id'::uuid ORDER BY em.created_at DESC LIMIT 1)
WHERE e.organization_id=:'organization_id'::uuid AND e.employee_number LIKE :'report_prefix'||'-%';
INSERT INTO timesheet_approvals(id,timesheet_id,manager_employee_id,decision,comment,decided_at)
SELECT gen_random_uuid(),t.id,e.manager_id,t.status,CASE WHEN t.status='REJECTED' THEN 'Please correct the recorded hours.' ELSE 'Local reporting fixture' END,now()
FROM timesheets t JOIN employees e ON e.id=t.employee_id
WHERE e.organization_id=:'organization_id'::uuid AND e.employee_number LIKE :'report_prefix'||'-%' AND t.status IN ('APPROVED','REJECTED') AND e.manager_id IS NOT NULL;
INSERT INTO sap_timesheet_exports(id,timesheet_id,status,external_reference,exported_at,failure_code,failure_message,created_at,updated_at)
SELECT gen_random_uuid(),t.id,CASE WHEN right(e.employee_number,1)='A' THEN 'SUCCEEDED' ELSE 'FAILED' END,
CASE WHEN right(e.employee_number,1)='A' THEN 'SAP-REPORT-'||t.id END,
CASE WHEN right(e.employee_number,1)='A' THEN now() END,
CASE WHEN right(e.employee_number,1)='G' THEN 'SAP_VALIDATION_ERROR' END,
CASE WHEN right(e.employee_number,1)='G' THEN 'SAP rejected the timesheet data.' END,now(),now()
FROM timesheets t JOIN employees e ON e.id=t.employee_id WHERE e.organization_id=:'organization_id'::uuid AND e.employee_number IN (:'report_prefix'||'-A',:'report_prefix'||'-G') AND t.year=2028 AND t.month=10;
INSERT INTO sap_export_jobs(id,export_id,timesheet_id,status,operation,payload,attempt_count,cycle_attempts,execution_count,next_attempt_at,last_attempt_at,last_error_code)
SELECT gen_random_uuid(),x.id,t.id,CASE WHEN x.status='SUCCEEDED' THEN 'COMPLETED' ELSE 'FAILED' END,'EXPORT',
jsonb_build_object('timesheetId',t.id,'employeeNumber',e.employee_number,'year',t.year,'month',t.month,'entries',
 (SELECT jsonb_agg(jsonb_build_object('projectCode',p.code,'workDate',a.work_date,'hours',a.hours,'description',a.description))
 FROM timesheet_entries a JOIN projects p ON p.id=a.project_id WHERE a.timesheet_id=t.id)),1,1,1,now(),now(),x.failure_code
FROM sap_timesheet_exports x JOIN timesheets t ON t.id=x.timesheet_id JOIN employees e ON e.id=t.employee_id
WHERE e.organization_id=:'organization_id'::uuid AND e.employee_number LIKE :'report_prefix'||'-%';
INSERT INTO sap_export_attempts(id,export_id,job_id,attempt_number,operation,started_at,finished_at,outcome,safe_error_code,duration_ms,external_reference)
SELECT gen_random_uuid(),x.id,j.id,1,'EXPORT',now(),now(),x.status,x.failure_code,0,x.external_reference
FROM sap_export_jobs j JOIN sap_timesheet_exports x ON x.id=j.export_id JOIN timesheets t ON t.id=x.timesheet_id
JOIN employees e ON e.id=t.employee_id WHERE e.organization_id=:'organization_id'::uuid AND e.employee_number LIKE :'report_prefix'||'-%';
COMMIT;
