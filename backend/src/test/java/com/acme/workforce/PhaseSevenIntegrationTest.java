package com.acme.workforce;
import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.employee.domain.Employee;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import com.acme.workforce.approval.application.ApprovalService;
import com.acme.workforce.approval.domain.ApprovalDecision;
import com.acme.workforce.notification.application.*;
import com.acme.workforce.notification.domain.*;
import com.acme.workforce.notification.infrastructure.NotificationStore;
import com.acme.workforce.notification.infrastructure.NotificationStore.Recipient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PhaseSevenIntegrationTest extends TenantFixtures {
 @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);
  p.add("app.notifications.batch-size",()->"2");p.add("app.notifications.max-attempts",()->"3");
  p.add("app.notifications.timesheet-reminder-day",()->"31");
 }
 @Autowired com.acme.workforce.sap.infrastructure.SapJobStore sapJobs;
 @Autowired MockMvc mvc;@Autowired JdbcTemplate sql;@Autowired IdentityService identity;@Autowired EmployeeRepository employees;
 @Autowired ApprovalService approval;@Autowired NotificationIntents intents;@Autowired NotificationEvaluation evaluation;
 @Autowired NotificationStore store;@Autowired NotificationWorker worker;@Autowired NotificationPolicy policy;@Autowired PlatformTransactionManager tm;
 @MockitoBean JwtDecoder decoder;@MockitoBean EmailGateway gateway;@MockitoBean(name="notificationClock") Clock clock;
 AtomicReference<Instant> now=new AtomicReference<>(); UUID employeeUser,employee,managerUser,manager,finance,admin;TransactionTemplate tx;
 UUID user(String subject,String role){
  var id=identity.current(Jwt.withTokenValue("test").header("alg","RS256").claim("email_verified",true).subject(subject).claim("email",subject+"@example.test").claim("name","Name "+subject).build()).getId();
  member(sql,id,role);return id;
 }
 UUID profile(UUID u,String number,UUID boss){return employees.saveAndFlush(new Employee(ORGANIZATION,u,number,boss)).getId();}
 UUID sheet(UUID owner,String status,int year,int month){
  var id=UUID.randomUUID();
  sql.update("INSERT INTO timesheets(id,employee_id,year,month,status,total_hours,submitted_at,version) VALUES (?,?,?,?,?,0,CASE WHEN ?='DRAFT' THEN NULL ELSE now() END,0)",id,owner,year,month,status,status);return id;
 }
 @BeforeEach void seed(){
  sql.execute("TRUNCATE app_users CASCADE");tx=new TransactionTemplate(tm);now.set(Instant.parse("2026-10-31T12:00:00Z"));
  when(clock.instant()).thenAnswer(i->now.get());when(clock.withZone(any())).thenAnswer(i->Clock.fixed(now.get(),i.getArgument(0)));
  when(gateway.send(any())).thenReturn(new EmailDeliveryResult(true));
  managerUser=user("manager","MANAGER");manager=profile(managerUser,"M-1",null);
  employeeUser=user("employee","EMPLOYEE");employee=profile(employeeUser,"E-1",manager);
  finance=user("finance","FINANCE");admin=user("admin","ADMIN");
 }
 long count(String type){return sql.queryForObject("SELECT count(*) FROM notifications WHERE type=?",Long.class,type);}
 String state(UUID id){return sql.queryForObject("SELECT status FROM notifications WHERE id=?",String.class,id);}
 UUID enqueue(String key){
  tx.executeWithoutResult(s->store.enqueue(NotificationType.TIMESHEET_APPROVED,new Recipient(ORGANIZATION,employeeUser,"employee@example.test","Name"),key,new NotificationTemplates.Content("Approved","Hello")));
  return sql.queryForObject("SELECT id FROM notifications WHERE deduplication_key=?",UUID.class,key);
 }
 void advance(Duration d){now.updateAndGet(t->t.plus(d));}
 @ParameterizedTest @EnumSource(ApprovalDecision.class)
 void decisionCreatesDurableDeduplicatedIntent(ApprovalDecision decision){
  var sheet=sheet(employee,"PENDING_APPROVAL",2026,10);
  when(gateway.send(any())).thenThrow(new EmailDeliveryException(EmailDeliveryException.Failure.CONNECTION));
  approval.decide(managerUser,sheet,0,decision,"Correct <script>Δοκιμή</script>");
  var id=sql.queryForObject("SELECT id FROM timesheet_approvals WHERE timesheet_id=?",UUID.class,sheet);
  tx.executeWithoutResult(s->{intents.approval(id);intents.approval(id);});
  assertThat(count("TIMESHEET_"+decision)).isEqualTo(1);
  assertThat(sql.queryForObject("SELECT body FROM notifications",String.class)).contains("<script>Δοκιμή</script>","2026/10");
  assertThat(sql.queryForObject("SELECT status FROM notifications",String.class)).isEqualTo("PENDING");
  verifyNoInteractions(gateway);
 }
 @Test void businessRollbackAlsoRollsBackIntent(){
  var id=sheet(employee,"PENDING_APPROVAL",2026,10);
  assertThatThrownBy(()->tx.executeWithoutResult(s->{approval.decide(managerUser,id,0,ApprovalDecision.APPROVED,null);throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
  assertThat(count("TIMESHEET_APPROVED")).isZero();
  assertThat(sql.queryForObject("SELECT status FROM timesheets WHERE id=?",String.class,id)).isEqualTo("PENDING_APPROVAL");
 }
 @ParameterizedTest @ValueSource(strings={"NO_TIMESHEET","DRAFT","REJECTED","PENDING_APPROVAL","APPROVED"})
 void employeeReminderEligibility(String status){
  if(!status.equals("NO_TIMESHEET"))sheet(employee,status,2026,10);
  evaluation.evaluate();evaluation.evaluate();
  long actual=sql.queryForObject("SELECT count(*) FROM notifications WHERE type='TIMESHEET_REMINDER' AND recipient_user_id=?",Long.class,employeeUser);
  assertThat(actual).isEqualTo(Set.of("PENDING_APPROVAL","APPROVED").contains(status)?0:1);
 }
 @Test void reminderCancelledAfterSubmission(){
  var id=sheet(employee,"DRAFT",2026,10);evaluation.evaluate();
  sql.update("UPDATE timesheets SET status='PENDING_APPROVAL',submitted_at=now() WHERE id=?",id);
  for(int i=0;i<5;i++)worker.runOnce();
  assertThat(sql.queryForObject("SELECT status FROM notifications WHERE recipient_user_id=? AND type='TIMESHEET_REMINDER'",String.class,employeeUser)).isEqualTo("CANCELLED");
  verify(gateway,never()).send(argThat(m->m.recipient().equals("employee@example.test")));
 }
 @Test void managerDigestContainsOnlyDirectReportsAndDeduplicates(){
  sheet(employee,"PENDING_APPROVAL",2026,10);
  var stranger=profile(user("stranger","EMPLOYEE"),"NOT-DIRECT",null);sheet(stranger,"PENDING_APPROVAL",2026,10);
  evaluation.evaluate();evaluation.evaluate();
  assertThat(count("APPROVAL_REMINDER")).isEqualTo(1);
  var body=sql.queryForObject("SELECT body FROM notifications WHERE type='APPROVAL_REMINDER'",String.class);
  assertThat(body).contains("E-1","/app/approvals").doesNotContain("NOT-DIRECT");
 }
 @ParameterizedTest @ValueSource(strings={"inactive","wrong-role","no-pending"})
 void ineligibleManagerGetsNoDigest(String reason){
  if(!reason.equals("no-pending"))sheet(employee,"PENDING_APPROVAL",2026,10);
  if(reason.equals("inactive"))sql.update("UPDATE employees SET active=false WHERE id=?",manager);
  if(reason.equals("wrong-role"))sql.update("UPDATE organization_memberships SET role='EMPLOYEE' WHERE user_id=?",managerUser);
  evaluation.evaluate();assertThat(count("APPROVAL_REMINDER")).isZero();
 }
 @Test void financeMonthlyDigestDeduplicates(){
  evaluation.evaluate();evaluation.evaluate();assertThat(count("FINANCE_MONTHLY_REMINDER")).isEqualTo(2);
  assertThat(sql.queryForObject("SELECT body FROM notifications WHERE type='FINANCE_MONTHLY_REMINDER' LIMIT 1",String.class)).contains("year=2026&month=9");
 }
 @Test void cleanPreviousMonthDoesNotNeedFinanceDigest(){
  sheet(employee,"APPROVED",2026,9);sheet(manager,"APPROVED",2026,9);
  evaluation.evaluate();assertThat(count("FINANCE_MONTHLY_REMINDER")).isZero();
 }
 @ParameterizedTest @ValueSource(strings={"FAILED","UNKNOWN","SUCCEEDED"})
 void sapAlertsAreRoleLimitedSafeAndDeduplicated(String status){
  var id=sheet(employee,"APPROVED",2026,10);var export=UUID.randomUUID();
  sql.update("INSERT INTO sap_timesheet_exports(id,timesheet_id,status,failure_code,failure_message,created_at,updated_at) VALUES (?,?,'FAILED','SAP_VALIDATION_ERROR','secret raw response',now(),now())",export,id);
  tx.executeWithoutResult(s->{intents.sap(export,1,status);intents.sap(export,1,status);});
  long expected=status.equals("SUCCEEDED")?0:2;
  assertThat(sql.queryForObject("SELECT count(*) FROM notifications",Long.class)).isEqualTo(expected);
  if(expected>0){
   assertThat(sql.queryForList("SELECT recipient_user_id FROM notifications",UUID.class)).containsExactlyInAnyOrder(finance,admin);
   assertThat(sql.queryForObject("SELECT body FROM notifications LIMIT 1",String.class)).contains(status,"SAP_VALIDATION_ERROR").doesNotContain("secret raw response");
   tx.executeWithoutResult(s->intents.sap(export,2,status));
   assertThat(count("SAP_EXPORT_"+status)).isEqualTo(status.equals("FAILED")?4:2);
  }
 }
 @Test void smtpOutsideTransactionAndClaimCommitted(){
  var id=enqueue("outside");
  when(gateway.send(any())).thenAnswer(i->{
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
   assertThat(state(id)).isEqualTo("PROCESSING");
   return new EmailDeliveryResult(true);
  });
  worker.runOnce();worker.runOnce();
  assertThat(state(id)).isEqualTo("SENT");verify(gateway,times(1)).send(any());
  assertThat(sql.queryForObject("SELECT sent_at FROM notifications WHERE id=?",Timestamp.class,id)).isNotNull();
  assertThat(sql.queryForObject("SELECT outcome FROM notification_attempts",String.class)).isEqualTo("SENT");
 }
 @Test void transientBackoffAndBoundedAttempts(){
  var id=enqueue("retry");
  for(int n=1;n<=3;n++){
   var claim=store.claim("worker").getFirst();assertThat(claim.attempt()).isEqualTo(n);
   store.finish(claim,false,EmailDeliveryException.Failure.TEMPORARY);
   if(n<3){
    var next=sql.queryForObject("SELECT next_attempt_at FROM notifications WHERE id=?",Timestamp.class,id).toInstant();
    assertThat(next).isEqualTo(now.get().plusSeconds(n==1?30:60));
    assertThat(store.claim("early")).isEmpty();now.set(next);
   }
  }
  assertThat(state(id)).isEqualTo("FAILED");assertThat(store.claim("done")).isEmpty();
  assertThat(sql.queryForObject("SELECT count(*) FROM notification_attempts",Long.class)).isEqualTo(3);
 }
 @ParameterizedTest @EnumSource(value=EmailDeliveryException.Failure.class,names={"AUTHENTICATION","RECIPIENT","CONTENT","PERMANENT"})
 void permanentFailureIsTerminal(EmailDeliveryException.Failure failure){
  var id=enqueue("permanent");store.finish(store.claim("worker").getFirst(),false,failure);assertThat(state(id)).isEqualTo("FAILED");assertThat(store.claim("again")).isEmpty();
 }
 @Test void batchAndFutureDatesHonored(){
  enqueue("one");enqueue("two");var later=enqueue("later");enqueue("three");
  sql.update("UPDATE notifications SET next_attempt_at=? WHERE id=?",Timestamp.from(now.get().plusSeconds(20)),later);
  assertThat(store.claim("a")).hasSize(2);assertThat(store.claim("b")).hasSize(1);assertThat(store.claim("c")).isEmpty();
 }
 @Test void concurrentWorkersCannotClaimSameNotification()throws Exception{
  enqueue("race");
  try(var pool=Executors.newFixedThreadPool(2)){
   var gate=new CountDownLatch(1);
   var a=pool.submit(()->{gate.await();return store.claim("a");});
   var b=pool.submit(()->{gate.await();return store.claim("b");});gate.countDown();
   assertThat(a.get().size()+b.get().size()).isEqualTo(1);
  }
 }
 @Test void skipLockedDoesNotWait()throws Exception{
  var id=enqueue("locked");var held=new CountDownLatch(1);var release=new CountDownLatch(1);
  try(var pool=Executors.newSingleThreadExecutor()){
   var lock=pool.submit(()->tx.executeWithoutResult(s->{
    sql.queryForObject("SELECT id FROM notifications WHERE id=? FOR UPDATE",UUID.class,id);held.countDown();
    try{assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new RuntimeException(e);}
   }));
   assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();
   try{assertThat(store.claim("other")).isEmpty();}finally{release.countDown();}
   lock.get();
  }
 }
 @Test void staleLeaseRecoversAndFencesOldWorker(){
  var id=enqueue("lease");var old=store.claim("old").getFirst();
  assertThat(store.claim("fresh")).isEmpty();advance(policy.lease().plusSeconds(1));
  assertThat(store.claim("recovery")).isEmpty();assertThat(state(id)).isEqualTo("PENDING");
  advance(policy.backoff(1));var current=store.claim("new").getFirst();
  assertThat(store.finish(old,true,null)).isFalse();assertThat(state(id)).isEqualTo("PROCESSING");
  assertThat(store.finish(current,true,null)).isTrue();assertThat(state(id)).isEqualTo("SENT");
  assertThat(sql.queryForList("SELECT outcome FROM notification_attempts ORDER BY attempt_number",String.class)).containsExactly("RETRY_SCHEDULED","SENT");
 }
 @Test void resultAndAttemptRollbackTogether(){
  var id=enqueue("atomic");var claim=store.claim("w").getFirst();
  sql.execute("CREATE FUNCTION fail_notification_result() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test'; END; $$");
  sql.execute("CREATE TRIGGER fail_notification_result BEFORE UPDATE ON notifications FOR EACH ROW EXECUTE FUNCTION fail_notification_result()");
  try{
   assertThatThrownBy(()->store.finish(claim,true,null)).isInstanceOf(Exception.class);
   assertThat(state(id)).isEqualTo("PROCESSING");assertThat(sql.queryForObject("SELECT count(*) FROM notification_attempts",Long.class)).isZero();
  }finally{sql.execute("DROP TRIGGER fail_notification_result ON notifications");sql.execute("DROP FUNCTION fail_notification_result()");}
 }
 @Test void attemptHistoryImmutable(){
  enqueue("immutable");store.finish(store.claim("w").getFirst(),true,null);
  assertThatThrownBy(()->sql.update("UPDATE notification_attempts SET outcome='FAILED'")).isInstanceOf(Exception.class);
  assertThatThrownBy(()->sql.update("DELETE FROM notification_attempts")).isInstanceOf(Exception.class);
 }
 @Test void concurrentEvaluationsAreIdempotent()throws Exception{
  try(var pool=Executors.newFixedThreadPool(2)){
   var gate=new CountDownLatch(1);
   var a=pool.submit(()->{gate.await();return evaluation.evaluate();});var b=pool.submit(()->{gate.await();return evaluation.evaluate();});
   gate.countDown();a.get();b.get();
  }
  assertThat(count("TIMESHEET_REMINDER")).isEqualTo(2);assertThat(count("FINANCE_MONTHLY_REMINDER")).isEqualTo(2);
 }
 @Test void duplicateDatabaseKeyIsRejected(){
  enqueue("same");enqueue("same");
  assertThat(sql.queryForObject("SELECT count(*) FROM notifications",Long.class)).isEqualTo(1);
  assertThatThrownBy(()->sql.update("INSERT INTO notifications SELECT gen_random_uuid(),type,recipient_user_id,recipient_email,subject,body,status,deduplication_key,attempt_count,next_attempt_at,sent_at,business_year,business_month,last_error_code,last_error_message,locked_at,locked_by,claim_token,created_at,updated_at,organization_id FROM notifications")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
 }
 @ParameterizedTest @CsvSource({"2027-02-27T22:30:00Z,2","2028-02-28T22:30:00Z,2","2027-02-26T22:30:00Z,0"})
 void timezoneAndShortMonths(String instant,long expected){
  var original=TimeZone.getDefault();
  try{TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));now.set(Instant.parse(instant));evaluation.evaluate();assertThat(count("TIMESHEET_REMINDER")).isEqualTo(expected);}
  finally{TimeZone.setDefault(original);}
 }
 @ParameterizedTest @EnumSource(value=com.acme.workforce.sap.domain.SapGatewayException.Failure.class,names={"VALIDATION","TIMEOUT","CONNECTION"})
 void sapWorkerCreatesOnlyOperationalAlerts(com.acme.workforce.sap.domain.SapGatewayException.Failure failure){
  var id=sheet(employee,"APPROVED",2026,10);var export=UUID.randomUUID();
  sql.update("INSERT INTO sap_timesheet_exports(id,timesheet_id,status,created_at,updated_at) VALUES (?,?,'QUEUED',now(),now())",export,id);
  tx.executeWithoutResult(s->sapJobs.create(export,new com.acme.workforce.sap.domain.ApprovedTimesheetPayload(id,"E-1",2026,10,List.of())));
  var claim=sapJobs.claim("sap-worker").getFirst();
  sapJobs.finish(claim,null,failure,false);
  assertThat(sql.queryForObject("SELECT count(*) FROM notifications",Long.class)).isEqualTo(failure==com.acme.workforce.sap.domain.SapGatewayException.Failure.CONNECTION?0:2);
  assertThat(sapJobs.finish(claim,null,failure,false)).isFalse();
  verifyNoInteractions(gateway);
 }
 @Test void roleRemovedBeforeDeliveryCancelsOperationalAlert(){
  var id=sheet(employee,"APPROVED",2026,10);var export=UUID.randomUUID();
  sql.update("INSERT INTO sap_timesheet_exports(id,timesheet_id,status,failure_code,failure_message,created_at,updated_at) VALUES (?,?,'FAILED','SAP_VALIDATION_ERROR','Safe failure',now(),now())",export,id);
  tx.executeWithoutResult(s->intents.sap(export,1,"FAILED"));
  sql.update("UPDATE organization_memberships SET role='EMPLOYEE' WHERE user_id=?",finance);
  worker.runOnce();
  assertThat(sql.queryForObject("SELECT status FROM notifications WHERE recipient_user_id=?",String.class,finance)).isEqualTo("CANCELLED");
  verify(gateway,never()).send(argThat(m->m.recipient().equals("finance@example.test")));
 }
 @Test void backoffIsCapped(){assertThat(policy.backoff(20)).isEqualTo(Duration.ofMinutes(10));}
 @Test void ownHistoryOnlyAndNoInternalFields()throws Exception{
  var id=enqueue("mine");
  mvc.perform(get("/api/v1/notifications").with(jwt().jwt(j->j.claim("email_verified",true).subject("employee")))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].recipientEmail").doesNotExist()).andExpect(jsonPath("$.content[0].deduplicationKey").doesNotExist());
  mvc.perform(get("/api/v1/notifications/"+id).with(jwt().jwt(j->j.claim("email_verified",true).subject("employee")))).andExpect(status().isOk());
  mvc.perform(get("/api/v1/notifications/"+id).with(jwt().jwt(j->j.claim("email_verified",true).subject("admin")))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/notifications").with(jwt().jwt(j->j.claim("email_verified",true).subject("manager")))).andExpect(jsonPath("$.totalElements").value(0));
 }
 @Test void unauthenticatedRejected()throws Exception{mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());}
 @Test void cannotSendArbitraryEmail()throws Exception{mvc.perform(post("/api/v1/notifications/send").with(jwt().jwt(j->j.claim("email_verified",true).subject("employee")))).andExpect(status().isMethodNotAllowed());}
}
