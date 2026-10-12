package com.acme.workforce;
import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.employee.domain.Employee;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PhaseFiveIntegrationTest extends TenantFixtures {
 @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 static final HttpServer server;
 static volatile java.util.function.Consumer<HttpExchange> handler;
 static {try{server=HttpServer.create(new InetSocketAddress("0.0.0.0",0),0);server.createContext("/sap/timesheets",e->handler.accept(e));server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());server.start();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);
  p.add("app.sap.base-url",()->"http://localhost:"+server.getAddress().getPort());p.add("app.sap.api-token",()->"test-only-secret");
  p.add("app.sap.worker.max-attempts",()->"3");p.add("app.sap.worker.batch-size",()->"2");p.add("app.sap.worker.initial-backoff",()->"1s");p.add("app.sap.worker.max-backoff",()->"4s");p.add("app.sap.worker.reconciliation-interval",()->"1s");
  p.add("app.sap.connect-timeout",()->"3s");p.add("app.sap.response-timeout",()->"3s");
 }
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate sql;
 @Autowired com.acme.workforce.sap.application.SapExportWorker worker;
 @Autowired IdentityService identity;@Autowired EmployeeRepository employees;
 @MockitoBean JwtDecoder decoder;
 UUID owner,manager,sheet; AtomicInteger calls; AtomicReference<JsonNode> captured; AtomicReference<String> key;
 @BeforeEach void setup(){
  sql.execute("TRUNCATE app_users,employees,projects,project_assignments,timesheets,timesheet_entries,timesheet_approvals,sap_timesheet_exports CASCADE");
  user("finance","FINANCE");user("admin","ADMIN");
  manager=employees.saveAndFlush(new Employee(ORGANIZATION,user("manager","MANAGER"),"M001",null)).getId();
  owner=employees.saveAndFlush(new Employee(ORGANIZATION,user("alice","EMPLOYEE"),"E001",manager)).getId();
  employees.saveAndFlush(new Employee(ORGANIZATION,user("other","EMPLOYEE"),"E002",null));
  sheet=UUID.randomUUID();var project=UUID.randomUUID();
  sql.update("INSERT INTO projects(organization_id,id,code,name,active) VALUES ('00000000-0000-0000-0000-000000000001',?,'PROJECT-A','Example',true)",project);
  sql.update("INSERT INTO timesheets(id,employee_id,year,month,status,total_hours,submitted_at,version) VALUES (?,?,2026,10,'APPROVED',6.25,now(),3)",sheet,owner);
  sql.update("INSERT INTO timesheet_entries(id,timesheet_id,project_id,work_date,hours,description) VALUES (?,?,?,'2026-10-05',6.25,?)",UUID.randomUUID(),sheet,project,"<text> & implementation");
  calls=new AtomicInteger();captured=new AtomicReference<>();key=new AtomicReference<>();
  handler=e->{try{calls.incrementAndGet();captured.set(json.readTree(e.getRequestBody()));key.set(e.getRequestHeaders().getFirst("Idempotency-Key"));respond(e,201,"{\"success\":true,\"externalReference\":\"SAP-TS-TEST\"}");}catch(Exception ex){throw new RuntimeException(ex);}};
 }
 @AfterAll static void stop(){server.stop(0);}
 UUID user(String subject,String role){var id=identity.current(Jwt.withTokenValue("test").header("alg","RS256").subject(subject).claim("name",subject).build()).getId();member(sql,id,role);return id;}
 String path(){return "/api/v1/sap/timesheets/"+sheet+"/export";}
 ResultActions req(MockHttpServletRequestBuilder r,String actor)throws Exception{return mvc.perform(r.with(jwt().jwt(j->j.subject(actor))));}
 ResultActions export(String actor)throws Exception{var queued=req(post(path()).header("X-Timesheet-Version",3),actor);if(queued.andReturn().getResponse().getStatus()!=202)return queued;worker.runOnce();return req(get(path()),actor);}
 static void respond(HttpExchange e,int status,String body){try{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();}catch(java.io.IOException ignored){e.close();}}

 @Autowired com.acme.workforce.sap.infrastructure.SapJobStore jobs;
 @Autowired com.acme.workforce.sap.application.SapWorkerPolicy policy;
 @Autowired com.acme.workforce.sap.domain.SapTimesheetGateway gateway;
 ResultActions queue(String actor)throws Exception{return req(post(path()).header("X-Timesheet-Version",3),actor);}
 void due(){sql.update("UPDATE sap_export_jobs SET next_attempt_at=now()-interval '1 second' WHERE status='PENDING'");}
 String state(){return sql.queryForObject("SELECT status FROM sap_timesheet_exports WHERE timesheet_id=?",String.class,sheet);}
 String jobState(){return sql.queryForObject("SELECT status FROM sap_export_jobs WHERE timesheet_id=?",String.class,sheet);}
 void remoteFailure(int status){handler=e->{if(e.getRequestMethod().equals("GET"))respond(e,404,"{\"code\":\"NOT_FOUND\"}");else{calls.incrementAndGet();respond(e,status,"{\"secret\":\"NEVER LEAK\"}");}};}
 void unknown()throws Exception{queue("finance");remoteFailure(503);worker.runOnce();assertThat(state()).isEqualTo("UNKNOWN");}
 void exhaust()throws Exception{
  queue("finance");remoteFailure(503);
  for(int i=0;i<policy.maxAttempts();i++){due();worker.runOnce();due();worker.runOnce();}
  assertThat(state()).isEqualTo("FAILED");
 }
 @Test void queueReturns202AndDoesNotCallSap()throws Exception{
  queue("finance").andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("QUEUED")).andExpect(jsonPath("$.job.attemptCount").value(0));
  assertThat(calls.get()).isZero();assertThat(jobState()).isEqualTo("PENDING");
 }
 @Test void concurrentEnqueueIsIdempotent()throws Exception{
  try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
   var a=pool.submit(()->queue("finance").andReturn().getResponse().getStatus());var b=pool.submit(()->queue("admin").andReturn().getResponse().getStatus());
   assertThat(a.get()).isEqualTo(202);assertThat(b.get()).isEqualTo(202);
  }
  assertThat(sql.queryForObject("SELECT count(*) FROM sap_export_jobs",Integer.class)).isEqualTo(1);
  assertThat(sql.queryForObject("SELECT count(*) FROM sap_timesheet_exports",Integer.class)).isEqualTo(1);assertThat(calls.get()).isZero();
 }
 @Test void twoWorkersCannotClaimSameJob()throws Exception{
  queue("finance");
  try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
   var a=pool.submit(()->jobs.claim("worker-a"));var b=pool.submit(()->jobs.claim("worker-b"));
   assertThat(a.get().size()+b.get().size()).isEqualTo(1);
  }
  assertThat(jobState()).isEqualTo("PROCESSING");
 }
 @Test void freshProcessingCannotBeStolen()throws Exception{queue("finance");assertThat(jobs.claim("a")).hasSize(1);assertThat(jobs.claim("b")).isEmpty();}
 @Test void futureJobsAreNotClaimed()throws Exception{
  queue("finance");sql.update("UPDATE sap_export_jobs SET next_attempt_at=now()+interval '1 hour'");assertThat(jobs.claim("a")).isEmpty();
 }
 @Test void batchLimitHonored()throws Exception{
  queue("finance");
  for(int month=1;month<=3;month++){
   var id=UUID.randomUUID();sql.update("INSERT INTO timesheets(id,employee_id,year,month,status,total_hours,submitted_at,version) VALUES (?,?,2027,?,'APPROVED',1,now(),3)",id,owner,month);
   req(post("/api/v1/sap/timesheets/"+id+"/export").header("X-Timesheet-Version",3),"finance").andExpect(status().isAccepted());
  }
  assertThat(jobs.claim("a")).hasSize(2);assertThat(jobs.claim("b")).hasSize(2);assertThat(jobs.claim("c")).isEmpty();
 }
 @Test void successCompletesJobAndAppendsHistory()throws Exception{
  queue("finance");worker.runOnce();assertThat(state()).isEqualTo("SUCCEEDED");assertThat(jobState()).isEqualTo("COMPLETED");
  req(get(path()+"/attempts"),"finance").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].outcome").value("SUCCEEDED"));
  assertThatThrownBy(()->sql.update("UPDATE sap_export_attempts SET outcome='FAILED'")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->sql.update("DELETE FROM sap_export_attempts")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @ParameterizedTest @ValueSource(ints={500,502,503,504})
 void serverFailureReconcilesBeforeRetry(int status)throws Exception{
  queue("finance");remoteFailure(status);worker.runOnce();
  assertThat(state()).isEqualTo("UNKNOWN");assertThat(calls.get()).isEqualTo(1);
  due();worker.runOnce();assertThat(state()).isEqualTo("QUEUED");assertThat(calls.get()).isEqualTo(1);
  assertThat(sql.queryForObject("SELECT next_attempt_at>now() FROM sap_export_jobs",Boolean.class)).isTrue();
 }
 @Test void connectionRefusalSchedulesBoundedRetry()throws Exception{
  queue("finance");var c=jobs.claim("a").getFirst();
  int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
  var adapter=new com.acme.workforce.sap.infrastructure.HttpSapTimesheetGateway(json,"http://127.0.0.1:"+port,"test",java.time.Duration.ofMillis(100),java.time.Duration.ofMillis(100));
  var ex=catchThrowableOfType(()->adapter.export(c.payload()),com.acme.workforce.sap.domain.SapGatewayException.class);
  assertThat(ex.failure()).isEqualTo(com.acme.workforce.sap.domain.SapGatewayException.Failure.CONNECTION);
  jobs.finish(c,null,ex.failure(),false);assertThat(state()).isEqualTo("QUEUED");assertThat(jobState()).isEqualTo("PENDING");
 }
 @Test void backoffIncreasesAndCaps(){
  assertThat(policy.backoff(1)).isEqualTo(java.time.Duration.ofSeconds(1));assertThat(policy.backoff(2)).isEqualTo(java.time.Duration.ofSeconds(2));assertThat(policy.backoff(20)).isEqualTo(java.time.Duration.ofSeconds(4));
 }
 @Test void maxAttemptsStopsAndRetainsSafeInformation()throws Exception{
  exhaust();assertThat(calls.get()).isEqualTo(3);assertThat(jobState()).isEqualTo("FAILED");assertThat(jobs.claim("a")).isEmpty();
  req(get(path()),"finance").andExpect(jsonPath("$.job.retryEligible").value(true)).andExpect(jsonPath("$.job.attemptCount").value(3));
  assertThat(sql.queryForObject("SELECT count(*) FROM sap_export_attempts",Integer.class)).isEqualTo(6);
 }
 @ParameterizedTest @ValueSource(ints={400,401,403,422})
 void permanentFailuresDoNotRetry(int code)throws Exception{
  queue("finance");remoteFailure(code);worker.runOnce();assertThat(state()).isEqualTo("FAILED");assertThat(jobState()).isEqualTo("FAILED");
  due();worker.runOnce();assertThat(calls.get()).isEqualTo(1);
  req(post(path()+"/retry"),"finance").andExpect(status().isConflict());
  assertThat(sql.queryForObject("SELECT failure_message FROM sap_timesheet_exports",String.class)).doesNotContain("NEVER LEAK");
 }
 @Test void payloadSnapshotAndIdentitySurviveRetries()throws Exception{
  queue("finance");remoteFailure(503);worker.runOnce();due();worker.runOnce();
  sql.update("UPDATE projects SET code='CHANGED'");sql.update("UPDATE employees SET employee_number='CHANGED' WHERE id=?",owner);
  handler=e->{try{captured.set(json.readTree(e.getRequestBody()));key.set(e.getRequestHeaders().getFirst("Idempotency-Key"));respond(e,201,"{\"success\":true,\"externalReference\":\"SAP-TS-STABLE\"}");}catch(Exception ex){throw new RuntimeException(ex);}};
  due();worker.runOnce();assertThat(state()).isEqualTo("SUCCEEDED");assertThat(key.get()).isEqualTo("SAP_TIMESHEET:"+sheet);
  assertThat(captured.get().path("PersonnelNumber").asText()).isEqualTo("E001");assertThat(captured.get().path("TimeEntries").get(0).path("ProjectCode").asText()).isEqualTo("PROJECT-A");
 }
 void timeout(boolean created)throws Exception{
  var releaseResponse=new CountDownLatch(1);
  handler=e->{
   if(e.getRequestMethod().equals("GET")){respond(e,created?200:404,created?"{\"success\":true,\"externalReference\":\"SAP-TS-FOUND\",\"exportIdentity\":\"SAP_TIMESHEET:"+sheet+"\"}":"{}");return;}
   calls.incrementAndGet();try{releaseResponse.await(30,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}respond(e,201,"{\"success\":true,\"externalReference\":\"SAP-TS-FOUND\"}");
  };
  queue("finance");try{worker.runOnce();assertThat(state()).isEqualTo("UNKNOWN");}finally{releaseResponse.countDown();}
 }
 @Test void timeoutAfterCreateReconcilesWithoutAnotherPost()throws Exception{
  timeout(true);req(post(path()+"/retry"),"finance").andExpect(status().isConflict());
  due();worker.runOnce();assertThat(state()).isEqualTo("SUCCEEDED");assertThat(calls.get()).isEqualTo(1);
 }
 @Test void timeoutBeforeCreateReconcilesMissingBeforeRetry()throws Exception{
  timeout(false);due();worker.runOnce();assertThat(state()).isEqualTo("QUEUED");assertThat(calls.get()).isEqualTo(1);
 }
 @Test void reconciliationFailureRemainsUnknownAndIsBounded()throws Exception{
  unknown();handler=e->respond(e,503,"{}");
  for(int i=0;i<3;i++){due();worker.runOnce();}
  assertThat(state()).isEqualTo("UNKNOWN");assertThat(jobState()).isEqualTo("FAILED");assertThat(jobs.claim("a")).isEmpty();
  req(post(path()+"/reconcile"),"admin").andExpect(status().isAccepted());assertThat(jobState()).isEqualTo("PENDING");
 }
 @Test void lookupRejectsMismatchedIdentity()throws Exception{
  unknown();handler=e->respond(e,200,"{\"success\":true,\"externalReference\":\"SAP-TS-X\",\"exportIdentity\":\"wrong\"}");
  due();worker.runOnce();assertThat(state()).isEqualTo("UNKNOWN");
 }
 @ParameterizedTest @ValueSource(strings={"finance","admin"})
 void manualRetryResetsBudgetButPreservesIdentityAndHistory(String actor)throws Exception{
  exhaust();var id=sql.queryForObject("SELECT id FROM sap_timesheet_exports",UUID.class);
  req(post(path()+"/retry"),actor).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("QUEUED"));
  assertThat(sql.queryForObject("SELECT cycle_attempts FROM sap_export_jobs",Integer.class)).isZero();
  assertThat(sql.queryForObject("SELECT attempt_count FROM sap_export_jobs",Integer.class)).isEqualTo(3);
  assertThat(sql.queryForObject("SELECT id FROM sap_timesheet_exports",UUID.class)).isEqualTo(id);
  assertThat(sql.queryForObject("SELECT count(*) FROM sap_export_attempts",Integer.class)).isEqualTo(6);
 }
 @ParameterizedTest @ValueSource(strings={"alice","manager"})
 void employeeManagerCannotQueueRetryOrReconcile(String actor)throws Exception{
  queue(actor).andExpect(status().isForbidden());unknown();
  req(post(path()+"/retry"),actor).andExpect(status().isForbidden());req(post(path()+"/reconcile"),actor).andExpect(status().isForbidden());
 }
 @Test void succeededAndProcessingCannotRetryOrReconcile()throws Exception{
  queue("finance");var c=jobs.claim("a").getFirst();
  req(post(path()+"/retry"),"admin").andExpect(status().isConflict());req(post(path()+"/reconcile"),"admin").andExpect(status().isConflict());
  worker.process(c);req(post(path()+"/retry"),"admin").andExpect(status().isConflict());req(post(path()+"/reconcile"),"admin").andExpect(status().isConflict());
 }
 @Test void staleLeaseRecoversViaLookupAndFencesLateCompletion()throws Exception{
  queue("finance");var old=jobs.claim("old").getFirst();
  sql.update("UPDATE sap_export_jobs SET locked_at=now()-interval '1 hour'");
  var fresh=jobs.claim("new").getFirst();assertThat(fresh.operation()).isEqualTo("RECONCILE");
  assertThat(jobs.finish(old,new com.acme.workforce.sap.domain.SapExportResult("SAP-TS-STALE"),null,false)).isFalse();
  handler=e->respond(e,200,"{\"success\":true,\"externalReference\":\"SAP-TS-RECOVERED\",\"exportIdentity\":\"SAP_TIMESHEET:"+sheet+"\"}");
  worker.process(fresh);assertThat(state()).isEqualTo("SUCCEEDED");
  assertThat(sql.queryForObject("SELECT count(*) FROM sap_export_attempts WHERE outcome='ABANDONED'",Integer.class)).isEqualTo(1);
  assertThat(sql.queryForObject("SELECT external_reference FROM sap_timesheet_exports",String.class)).isEqualTo("SAP-TS-RECOVERED");
 }
 @Test void claimsCommitBeforeHttpAndFailuresReleaseLocks()throws Exception{
  queue("finance");var observed=new AtomicBoolean();
  handler=e->{observed.set("PROCESSING".equals(jobState()) && sql.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND state='idle in transaction'",Integer.class)==0);respond(e,503,"{}");};
  worker.runOnce();assertThat(observed.get()).isTrue();assertThat(state()).isEqualTo("UNKNOWN");
  assertThat(sql.queryForObject("SELECT locked_at IS NULL AND claim_token IS NULL FROM sap_export_jobs",Boolean.class)).isTrue();
 }
 @Test void resultTransactionRollsBackExportJobAndHistoryTogether()throws Exception{
  queue("finance");var c=jobs.claim("a").getFirst();
  sql.execute("CREATE FUNCTION fail_job_finish() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='COMPLETED' THEN RAISE EXCEPTION 'test rollback'; END IF; RETURN NEW; END; $$");
  sql.execute("CREATE TRIGGER fail_job_finish BEFORE UPDATE ON sap_export_jobs FOR EACH ROW EXECUTE FUNCTION fail_job_finish()");
  try{
   assertThatThrownBy(()->jobs.finish(c,new com.acme.workforce.sap.domain.SapExportResult("SAP-TS-ROLLBACK"),null,false)).isInstanceOf(org.springframework.dao.DataAccessException.class);
   assertThat(state()).isEqualTo("PROCESSING");assertThat(jobState()).isEqualTo("PROCESSING");assertThat(sql.queryForObject("SELECT count(*) FROM sap_export_attempts",Integer.class)).isZero();
  }finally{sql.execute("DROP TRIGGER fail_job_finish ON sap_export_jobs");sql.execute("DROP FUNCTION fail_job_finish()");}
  jobs.finish(c,new com.acme.workforce.sap.domain.SapExportResult("SAP-TS-ATOMIC"),null,false);assertThat(state()).isEqualTo("SUCCEEDED");
 }
 @Test void anonymousManualActionsRejected()throws Exception{
  mvc.perform(post(path()+"/retry")).andExpect(status().isUnauthorized());mvc.perform(post(path()+"/reconcile")).andExpect(status().isUnauthorized());
 }

 @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
 @ParameterizedTest @ValueSource(booleans={true,false})
 void manualActionWaitingBehindCompletionCannotRequeueSuccess(boolean reconcile)throws Exception{
  if(reconcile){unknown();due();jobs.claim("worker");}else{exhaust();}
  var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
  try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
   var completing=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx->{
    sql.queryForList("SELECT id FROM sap_export_jobs FOR UPDATE");locked.countDown();
    try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Test release timeout");}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
    sql.update("UPDATE sap_timesheet_exports SET status='SUCCEEDED',external_reference='SAP-TS-CONCURRENT',exported_at=now(),failure_code=NULL,failure_message=NULL");
    sql.update("UPDATE sap_export_jobs SET status='COMPLETED',locked_at=NULL,locked_by=NULL,claim_token=NULL");
   }));
   assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
   var manual=pool.submit(()->req(post(path()+(reconcile?"/reconcile":"/retry")),"finance").andReturn().getResponse().getStatus());
   try{
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
    while(System.nanoTime()<deadline){
     waiting=sql.queryForObject("SELECT count(*)>0 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE '%sap_export_jobs%'",Boolean.class);
     if(waiting)break;Thread.sleep(10);
    }
    assertThat(waiting).isTrue();
   }finally{release.countDown();}
   completing.get(10,TimeUnit.SECONDS);assertThat(manual.get(10,TimeUnit.SECONDS)).isEqualTo(409);
  }
  assertThat(state()).isEqualTo("SUCCEEDED");assertThat(jobState()).isEqualTo("COMPLETED");
 }

}