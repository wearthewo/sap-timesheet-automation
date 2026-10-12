package com.acme.workforce;
import com.acme.workforce.notification.application.*;
import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.employee.domain.Employee;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PhaseEightNotificationTest extends TenantFixtures {
 @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){
  p.add("app.notifications.timesheet-reminder-day",()->"31");p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);
 }
 @Autowired JdbcTemplate sql;@Autowired IdentityService identity;@Autowired EmployeeRepository employees;
 @Autowired NotificationEvaluation evaluation;@Autowired NotificationIntents intents;@Autowired PlatformTransactionManager tm;
 @MockitoBean JwtDecoder decoder;@MockitoBean(name="notificationClock") Clock clock;
 UUID employee,employeeUser,manager,managerUser,finance;TransactionTemplate tx;
 UUID user(String sub,String name,String role){
  var id=identity.current(Jwt.withTokenValue("test").header("alg","RS256").subject(sub).claim("email",sub+"@example.test").claim("name",name).build()).getId();
  member(sql,id,role);return id;
 }
 UUID sheet(UUID owner,String state,int year,int month){
  var id=UUID.randomUUID();sql.update("INSERT INTO timesheets(id,employee_id,year,month,status,submitted_at) VALUES (?,?,?,?,?,CASE WHEN ?='DRAFT' THEN NULL ELSE now() END)",id,owner,year,month,state,state);return id;
 }
 @BeforeEach void seed(){
  sql.execute("TRUNCATE app_users CASCADE");tx=new TransactionTemplate(tm);
  when(clock.instant()).thenReturn(Instant.parse("2026-10-28T12:00:00Z"));
  when(clock.withZone(any())).thenAnswer(i->Clock.fixed(clock.instant(),i.getArgument(0)));
  managerUser=user("manager","Μαρία García & \"Review\"","MANAGER");
  manager=employees.saveAndFlush(new Employee(ORGANIZATION,managerUser,"M008",null)).getId();
  employeeUser=user("employee","Νίκος André & \"Employee\"","EMPLOYEE");
  employee=employees.saveAndFlush(new Employee(ORGANIZATION,employeeUser,"E008",manager)).getId();
  finance=user("finance","Finance","FINANCE");
 }
 String body(String type,UUID recipient){return sql.queryForObject("SELECT body FROM notifications WHERE type=? AND recipient_user_id=?",String.class,type,recipient);}
 @Test void digestNamesManagerAndOnlyDirectReports(){
  sheet(employee,"PENDING_APPROVAL",2026,10);
  var other=employees.saveAndFlush(new Employee(ORGANIZATION,user("other","Unrelated Employee","EMPLOYEE"),"PRIVATE",null)).getId();
  sheet(other,"PENDING_APPROVAL",2026,10);evaluation.evaluate();
  assertThat(body("APPROVAL_REMINDER",managerUser)).contains("Hello Μαρία García & \"Review\"","E008","Νίκος André","2026-10","/app/approvals").doesNotContain("PRIVATE","Unrelated Employee");
 }
 @Test void decisionIncludesAuthoritativeManagerAndAthensDecisionTime(){
  var id=sheet(employee,"APPROVED",2026,10);var decision=UUID.randomUUID();
  sql.update("INSERT INTO timesheet_approvals(id,timesheet_id,manager_employee_id,decision,comment,decided_at) VALUES (?,?,?,'APPROVED',?,'2026-10-25T01:30:00Z')",decision,id,manager,"Checked & accepted\nΔοκιμή <b>text</b>");
  tx.executeWithoutResult(s->intents.approval(decision));
  assertThat(body("TIMESHEET_APPROVED",employeeUser)).contains("Μαρία García","2026-10-25 03:30","Europe/Athens","Checked & accepted\nΔοκιμή <b>text</b>");
 }
 @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"FAILED","UNKNOWN"})
 void sapAlertIncludesStableIdentity(String state){
  var id=sheet(employee,"APPROVED",2026,10);var export=UUID.randomUUID();
  sql.update("INSERT INTO sap_timesheet_exports(id,timesheet_id,status,failure_code,failure_message,created_at,updated_at) VALUES (?,?,?,'SAP_TIMEOUT','Safe failure',now(),now())",export,id,state);
  tx.executeWithoutResult(s->intents.sap(export,1,state));
  assertThat(body("SAP_EXPORT_"+state,finance)).contains("SAP_TIMESHEET:"+id,"E008","2026-10","Status: "+state,"Code: SAP_TIMEOUT","/app/sap/"+id).doesNotContain("Safe failure");
  if(state.equals("UNKNOWN"))assertThat(body("SAP_EXPORT_UNKNOWN",finance)).contains("Reconciliation is required","do not blindly retry");
 }
 @Test void financeSeparatesMissingAndPendingCounts(){
  sheet(employee,"PENDING_APPROVAL",2026,9);evaluation.evaluate();
  assertThat(body("FINANCE_MONTHLY_REMINDER",finance)).contains("Missing timesheets: 1","Pending approval: 1","2026-09","year=2026&month=9");
 }

 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.CsvSource({
  "2026-01-30T22:30:00Z,2026-01,2","2026-01-30T20:30:00Z,2026-01,0",
  "2027-02-27T22:30:00Z,2027-02,2","2028-02-28T22:30:00Z,2028-02,2",
  "2028-02-27T22:30:00Z,2028-02,0","2026-04-29T21:30:00Z,2026-04,2",
  "2026-05-30T21:30:00Z,2026-05,2","2026-12-31T22:30:00Z,2027-01,0"})
 void calendarBoundariesIndependentOfJvm(String instant,String month,int expected){
  var old=TimeZone.getDefault();
  try{
   TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
   when(clock.instant()).thenReturn(Instant.parse(instant));evaluation.evaluate();evaluation.evaluate();
   assertThat(sql.queryForObject("SELECT count(*) FROM notifications WHERE type='TIMESHEET_REMINDER'",Integer.class)).isEqualTo(expected);
   if(expected>0)assertThat(body("TIMESHEET_REMINDER",employeeUser)).contains("Hello Νίκος André & \"Employee\"","Your "+month+" timesheet is NO_TIMESHEET","/app/timesheets/"+YearMonth.parse(month).getYear()+"/"+YearMonth.parse(month).getMonthValue());
  }finally{TimeZone.setDefault(old);}
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.CsvSource({
  "2026-03-29T00:30:00Z,2026-03-29 02:30 +02:00",
  "2026-03-29T01:30:00Z,2026-03-29 04:30 +03:00",
  "2026-10-25T00:30:00Z,2026-10-25 03:30 +03:00",
  "2026-10-25T01:30:00Z,2026-10-25 03:30 +02:00"})
 void decisionDisplayHandlesAthensDst(String utc,String local){
  var id=sheet(employee,"REJECTED",2026,3);var decision=UUID.randomUUID();
  String comment="Διορθώστε τις ώρες & \"project\"\n<script>plain text</script>";
  sql.update("INSERT INTO timesheet_approvals(id,timesheet_id,manager_employee_id,decision,comment,decided_at) VALUES (?,?,?,'REJECTED',?,?::timestamptz)",decision,id,manager,comment,utc);
  tx.executeWithoutResult(s->{intents.approval(decision);intents.approval(decision);});
  var row=sql.queryForMap("SELECT recipient_email,subject,body FROM notifications WHERE type='TIMESHEET_REJECTED'");
  assertThat(row.get("recipient_email")).isEqualTo("employee@example.test");
  assertThat((String)row.get("subject")).contains("rejected","2026-03");
  assertThat((String)row.get("body")).contains("Νίκος André",comment,local+" [Europe/Athens]","Please reopen","/app/timesheets/2026/3");
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"DRAFT","REJECTED","PENDING_APPROVAL","APPROVED"})
 void reminderExactConditionAndRecipient(String state){
  when(clock.instant()).thenReturn(Instant.parse("2026-10-31T12:00:00Z"));
  sheet(employee,state,2026,10);evaluation.evaluate();
  var rows=sql.queryForList("SELECT recipient_email,subject,body FROM notifications WHERE type='TIMESHEET_REMINDER' AND recipient_user_id=?",employeeUser);
  if(Set.of("PENDING_APPROVAL","APPROVED").contains(state))assertThat(rows).isEmpty();
  else{
   assertThat(rows).hasSize(1);assertThat(rows.getFirst().get("recipient_email")).isEqualTo("employee@example.test");
   assertThat((String)rows.getFirst().get("body")).contains("Hello Νίκος André & \"Employee\"","Your 2026-10 timesheet is "+state,"/app/timesheets/2026/10");
  }
 }
 @Test void digestPendingCountAndNamesAreExact(){
  sheet(employee,"PENDING_APPROVAL",2026,10);
  var second=employees.saveAndFlush(new Employee(ORGANIZATION,user("second","Élodie & \"Two\"","EMPLOYEE"),"E009",manager)).getId();sheet(second,"PENDING_APPROVAL",2026,10);
  evaluation.evaluate();evaluation.evaluate();
  assertThat(body("APPROVAL_REMINDER",managerUser)).contains("E008","E009","Élodie & \"Two\"");
  assertThat(sql.queryForObject("SELECT subject FROM notifications WHERE type='APPROVAL_REMINDER'",String.class)).isEqualTo("2 timesheets waiting for approval — 2026-10");
 }
 @Test void snapshotIdentityAndDeletionAreGuardedButExecutionCanAdvance(){
  var id=sheet(employee,"APPROVED",2026,10);var export=UUID.randomUUID();
  sql.update("INSERT INTO sap_timesheet_exports(id,timesheet_id,status,created_at,updated_at) VALUES (?,?,'QUEUED',now(),now())",export,id);
  sql.update("INSERT INTO sap_export_jobs(id,export_id,timesheet_id,status,operation,payload,next_attempt_at) VALUES (?,?,?,'PENDING','EXPORT','{}',now())",UUID.randomUUID(),export,id);
  assertThatThrownBy(()->sql.update("UPDATE sap_export_jobs SET timesheet_id=?",UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->sql.update("DELETE FROM sap_export_jobs")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(sql.update("UPDATE sap_export_jobs SET next_attempt_at=now()+interval '1 minute'")).isEqualTo(1);
 }

 @Test void persistedSapPayloadCannotBeChanged(){
  var id=sheet(employee,"APPROVED",2026,10);var export=UUID.randomUUID();
  sql.update("INSERT INTO sap_timesheet_exports(id,timesheet_id,status,created_at,updated_at) VALUES (?,?,'QUEUED',now(),now())",export,id);
  sql.update("INSERT INTO sap_export_jobs(id,export_id,timesheet_id,status,operation,payload,next_attempt_at) VALUES (?,?,?,'PENDING','EXPORT','{}',now())",UUID.randomUUID(),export,id);
  assertThatThrownBy(()->sql.update("UPDATE sap_export_jobs SET payload='{\"changed\":true}'")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
}
