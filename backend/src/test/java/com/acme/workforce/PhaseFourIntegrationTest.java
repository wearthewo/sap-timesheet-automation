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
class PhaseFourIntegrationTest {
 @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 static final HttpServer server;
 static volatile java.util.function.Consumer<HttpExchange> handler;
 static {try{server=HttpServer.create(new InetSocketAddress("0.0.0.0",0),0);server.createContext("/sap/timesheets",e->handler.accept(e));server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());server.start();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);
  p.add("app.sap.base-url",()->"http://localhost:"+server.getAddress().getPort());p.add("app.sap.api-token",()->"test-only-secret");
  p.add("app.sap.connect-timeout",()->"1s");p.add("app.sap.response-timeout",()->"600ms");
 }
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate sql;
 @Autowired com.acme.workforce.sap.application.SapExportWorker worker;
 @Autowired IdentityService identity;@Autowired EmployeeRepository employees;
 @MockitoBean JwtDecoder decoder;
 UUID owner,manager,sheet; AtomicInteger calls; AtomicReference<JsonNode> captured; AtomicReference<String> key;
 @BeforeEach void setup(){
  sql.execute("TRUNCATE app_users,employees,projects,project_assignments,timesheets,timesheet_entries,timesheet_approvals,sap_timesheet_exports CASCADE");
  user("finance","FINANCE");user("admin","ADMIN");
  manager=employees.saveAndFlush(new Employee(user("manager","MANAGER"),"M001",null)).getId();
  owner=employees.saveAndFlush(new Employee(user("alice","EMPLOYEE"),"E001",manager)).getId();
  employees.saveAndFlush(new Employee(user("other","EMPLOYEE"),"E002",null));
  sheet=UUID.randomUUID();var project=UUID.randomUUID();
  sql.update("INSERT INTO projects(id,code,name,active) VALUES (?,'PROJECT-A','Example',true)",project);
  sql.update("INSERT INTO timesheets(id,employee_id,year,month,status,total_hours,submitted_at,version) VALUES (?,?,2026,10,'APPROVED',6.25,now(),3)",sheet,owner);
  sql.update("INSERT INTO timesheet_entries(id,timesheet_id,project_id,work_date,hours,description) VALUES (?,?,?,'2026-10-05',6.25,?)",UUID.randomUUID(),sheet,project,"<text> & implementation");
  calls=new AtomicInteger();captured=new AtomicReference<>();key=new AtomicReference<>();
  handler=e->{try{calls.incrementAndGet();captured.set(json.readTree(e.getRequestBody()));key.set(e.getRequestHeaders().getFirst("Idempotency-Key"));respond(e,201,"{\"success\":true,\"externalReference\":\"SAP-TS-TEST\"}");}catch(Exception ex){throw new RuntimeException(ex);}};
 }
 @AfterAll static void stop(){server.stop(0);}
 UUID user(String subject,String role){var id=identity.current(Jwt.withTokenValue("test").header("alg","RS256").subject(subject).claim("name",subject).build()).getId();sql.update("UPDATE app_users SET role=? WHERE id=?",role,id);return id;}
 String path(){return "/api/v1/sap/timesheets/"+sheet+"/export";}
 ResultActions req(MockHttpServletRequestBuilder r,String actor)throws Exception{return mvc.perform(r.with(jwt().jwt(j->j.subject(actor))));}
 ResultActions export(String actor)throws Exception{var queued=req(post(path()).header("X-Timesheet-Version",3),actor);if(queued.andReturn().getResponse().getStatus()!=202)return queued;worker.runOnce();return req(get(path()),actor);}
 static void respond(HttpExchange e,int status,String body){try{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();}catch(java.io.IOException ignored){e.close();}}
 @ParameterizedTest @ValueSource(strings={"finance","admin"})
 void authorizedSuccessPersistsReferenceAndTimestamp(String actor)throws Exception{
  export(actor).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.externalReference").value("SAP-TS-TEST")).andExpect(jsonPath("$.exportedAt").isNotEmpty());
  assertThat(sql.queryForObject("SELECT external_reference FROM sap_timesheet_exports WHERE timesheet_id=?",String.class,sheet)).isEqualTo("SAP-TS-TEST");
  assertThat(sql.queryForObject("SELECT version FROM timesheets WHERE id=?",Long.class,sheet)).isEqualTo(3);
  export(actor).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
  assertThat(calls.get()).isEqualTo(1);
 }
 @ParameterizedTest @ValueSource(strings={"DRAFT","PENDING_APPROVAL","REJECTED"})
 void nonApprovedCannotExport(String state)throws Exception{
  sql.update("UPDATE timesheets SET status=?,submitted_at=CASE WHEN ?='DRAFT' THEN NULL ELSE now() END WHERE id=?",state,state,sheet);
  export("finance").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAP_EXPORT_NOT_ALLOWED"));
  assertThat(calls.get()).isZero();
 }
 @ParameterizedTest @ValueSource(strings={"alice","manager","other"})
 void unauthorizedRolesCannotExportOrBrowse(String actor)throws Exception{
  export(actor).andExpect(status().isForbidden());
  req(get("/api/v1/sap/timesheets"),actor).andExpect(status().isForbidden());
  req(get("/api/v1/sap/timesheets/"+sheet),actor).andExpect(status().isForbidden());
  assertThat(calls.get()).isZero();
 }
 @Test void anonymousCannotAccess()throws Exception{
  mvc.perform(post(path()).header("X-Timesheet-Version",3)).andExpect(status().isUnauthorized());
  mvc.perform(get(path())).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/sap/timesheets")).andExpect(status().isUnauthorized());
 }
 @Test void staleVersionDoesNotReserve()throws Exception{
  req(post(path()).header("X-Timesheet-Version",2),"finance").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
  assertThat(sql.queryForObject("SELECT count(*) FROM sap_timesheet_exports",Integer.class)).isZero();
 }
 @Test void missingVersionRejected()throws Exception{req(post(path()),"finance").andExpect(status().isBadRequest());}
 @Test void mappingAndStableIdentity()throws Exception{
  export("finance").andExpect(status().isOk());
  assertThat(captured.get().path("PersonnelNumber").asText()).isEqualTo("E001");
  assertThat(captured.get().path("CalendarYear").asInt()).isEqualTo(2026);
  assertThat(captured.get().path("CalendarMonth").asInt()).isEqualTo(10);
  var entry=captured.get().path("TimeEntries").get(0);
  assertThat(entry.path("ProjectCode").asText()).isEqualTo("PROJECT-A");
  assertThat(entry.path("Hours").asText()).isEqualTo("6.25");
  assertThat(entry.path("WorkDate").asText()).isEqualTo("2026-10-05");
  assertThat(entry.path("Description").asText()).isEqualTo("<text> & implementation");
  assertThat(key.get()).isEqualTo("SAP_TIMESHEET:"+sheet);
  assertThat(captured.get().path("ExternalSystemReference").asText()).isEqualTo(key.get());
 }
 @ParameterizedTest @CsvSource({"400,SAP_VALIDATION_ERROR,FAILED","401,SAP_AUTHENTICATION_FAILED,FAILED","403,SAP_AUTHENTICATION_FAILED,FAILED","409,SAP_DUPLICATE,UNKNOWN","500,SAP_UNAVAILABLE,UNKNOWN","503,SAP_UNAVAILABLE,UNKNOWN","302,SAP_EXPORT_UNKNOWN,UNKNOWN"})
 void safeFailureMapping(int status,String code,String state)throws Exception{
  handler=e->{calls.incrementAndGet();respond(e,status,"{\"message\":\"SECRET upstream stack trace and credentials\"}");};
  var response=export("finance").andExpect(status().isOk()).andExpect(jsonPath("$.failureCode").value(code)).andReturn().getResponse().getContentAsString();
  assertThat(response).doesNotContain("SECRET","stack trace","credentials");
  req(get(path()),"finance").andExpect(status().isOk()).andExpect(jsonPath("$.status").value(state)).andExpect(jsonPath("$.failureCode").value(code)).andExpect(jsonPath("$.externalReference").isEmpty()).andExpect(jsonPath("$.exportedAt").isEmpty());
  export("finance").andExpect(status().isOk());
  assertThat(sql.queryForObject("SELECT count(*) FROM sap_timesheet_exports",Integer.class)).isEqualTo(1);assertThat(calls.get()).isEqualTo(1);
 }
 @ParameterizedTest @ValueSource(strings={"{\"success\":true}","not-json","{\"success\":true,\"externalReference\":\"unsafe\\nreference\"}","{\"success\":false,\"externalReference\":\"SAP-TS-X\"}"})
 void malformedSuccessIsUnknown(String body)throws Exception{
  handler=e->respond(e,200,body);
  export("admin").andExpect(status().isOk()).andExpect(jsonPath("$.failureCode").value("SAP_EXPORT_UNKNOWN"));
  assertThat(sql.queryForObject("SELECT status FROM sap_timesheet_exports",String.class)).isEqualTo("UNKNOWN");
 }
 @Test void timeoutIsUnknownAndNoSecondAttempt()throws Exception{
  handler=e->{calls.incrementAndGet();try{Thread.sleep(1200);}catch(InterruptedException ex){Thread.currentThread().interrupt();}respond(e,201,"{\"success\":true,\"externalReference\":\"SAP-TS-LATE\"}");};
  export("finance").andExpect(status().isOk()).andExpect(jsonPath("$.failureCode").value("SAP_TIMEOUT"));
  req(get(path()),"finance").andExpect(jsonPath("$.status").value("UNKNOWN")).andExpect(jsonPath("$.idempotencyKey").value("SAP_TIMESHEET:"+sheet));
  export("finance").andExpect(status().isOk());assertThat(calls.get()).isEqualTo(1);
 }
 @Test void reservationCommittedAndNoDatabaseTransactionSpansHttp()throws Exception{
  var observed=new AtomicReference<String>();var inTransaction=new AtomicInteger(-1);
  handler=e->{
   observed.set(sql.queryForObject("SELECT status FROM sap_timesheet_exports WHERE timesheet_id=?",String.class,sheet));
   inTransaction.set(sql.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND state='idle in transaction'",Integer.class));
   respond(e,201,"{\"success\":true,\"externalReference\":\"SAP-TS-COMMITTED\"}");
  };
  export("admin").andExpect(status().isOk());assertThat(observed.get()).isEqualTo("PROCESSING");assertThat(inTransaction.get()).isZero();
  var result=sql.queryForMap("SELECT status,external_reference,exported_at,failure_code FROM sap_timesheet_exports");
  assertThat(result.get("status")).isEqualTo("SUCCEEDED");assertThat(result.get("external_reference")).isNotNull();assertThat(result.get("exported_at")).isNotNull();assertThat(result.get("failure_code")).isNull();
 }
 @Test void concurrentRequestsCreateOneReservationAndOneHttpCall()throws Exception{
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  handler=e->{calls.incrementAndGet();entered.countDown();try{release.await(400,TimeUnit.MILLISECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}respond(e,201,"{\"success\":true,\"externalReference\":\"SAP-TS-ONCE\"}");};
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
   var first=executor.submit(()->export("finance").andReturn().getResponse().getStatus());
   assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
   export("admin").andExpect(status().isOk());release.countDown();assertThat(first.get(10,TimeUnit.SECONDS)).isEqualTo(200);
  }
  assertThat(calls.get()).isEqualTo(1);assertThat(sql.queryForObject("SELECT count(*) FROM sap_timesheet_exports",Integer.class)).isEqualTo(1);
 }
 @Test void ownerAndDirectManagerReadOnlyStateButOtherEmployeeCannot()throws Exception{
  export("finance").andExpect(status().isOk());
  req(get(path()),"alice").andExpect(status().isOk());req(get(path()),"manager").andExpect(status().isOk());
  req(get(path()),"other").andExpect(status().isForbidden());
 }
 @Test void missingExportReturns404AndWorklistIncludesState()throws Exception{
  req(get(path()),"finance").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SAP_EXPORT_NOT_FOUND"));
  req(get("/api/v1/sap/timesheets"),"finance").andExpect(status().isOk()).andExpect(jsonPath("$.content[0].employee.employeeNumber").value("E001")).andExpect(jsonPath("$.content[0].export").isEmpty());
  export("admin").andExpect(status().isOk());
  req(get("/api/v1/sap/timesheets/"+sheet),"admin").andExpect(status().isOk()).andExpect(jsonPath("$.export.status").value("SUCCEEDED")).andExpect(jsonPath("$.timesheet.entries[0].hours").value("6.25"));
 }
}
