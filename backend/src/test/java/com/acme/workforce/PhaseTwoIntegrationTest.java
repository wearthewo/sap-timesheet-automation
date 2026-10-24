package com.acme.workforce;

import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.employee.domain.Employee;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import tools.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PhaseTwoIntegrationTest extends TenantFixtures {
 @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
  p.add("spring.datasource.url",postgres::getJdbcUrl); p.add("spring.datasource.username",postgres::getUsername); p.add("spring.datasource.password",postgres::getPassword);
 }
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper mapper;
 @Autowired JdbcTemplate sql;
 @Autowired IdentityService identity;
 @Autowired EmployeeRepository employees;
 @MockitoBean JwtDecoder decoder;
 UUID employee, project, second;
 @BeforeEach void setup() {
  sql.execute("TRUNCATE app_users, employees, projects, project_assignments, timesheets, timesheet_entries CASCADE");
  employee=employee("alice"); employee("bob");
  project=UUID.randomUUID(); second=UUID.randomUUID();
  for(UUID id:List.of(project,second)) {
   sql.update("INSERT INTO projects(organization_id,id,code,name,active) VALUES ('00000000-0000-0000-0000-000000000001',?,?,?,true)",id,id.toString(),"Project");
   sql.update("INSERT INTO project_assignments(id,employee_id,project_id,valid_from,valid_to,active) VALUES (?,?,?,'2026-10-01','2026-10-31',true)",UUID.randomUUID(),employee,id);
  }
 }
 UUID employee(String subject) {
  var token=Jwt.withTokenValue("test").header("alg","RS256").claim("email_verified",true).subject(subject).claim("email",subject+"@example.test").build();
  return employees.saveAndFlush(new Employee(ORGANIZATION,member(sql,identity.current(token).getId(),"EMPLOYEE"),subject,null)).getId();
 }
 ResultActions request(MockHttpServletRequestBuilder r,String subject) throws Exception { return mvc.perform(r.with(jwt().jwt(j->j.claim("email_verified",true).subject(subject)))); }
 JsonNode json(ResultActions r) throws Exception { return mapper.readTree(r.andReturn().getResponse().getContentAsString()); }
 JsonNode create(String subject) throws Exception {
  return json(request(post("/api/v1/timesheets").contentType("application/json").content("{\"year\":2026,\"month\":10}"),subject).andExpect(status().isCreated()));
 }
 String base(JsonNode sheet) { return "/api/v1/timesheets/"+sheet.get("id").asText(); }
 String body(UUID p,String date,String hours) throws Exception { return mapper.writeValueAsString(Map.of("projectId",p,"workDate",date,"hours",hours,"description","Implementation")); }
 MockHttpServletRequestBuilder addRequest(JsonNode s,UUID p,String date,String hours) throws Exception {
  return post(base(s)+"/entries").header("X-Timesheet-Version",s.get("version").asLong()).contentType("application/json").content(body(p,date,hours));
 }
 JsonNode add(JsonNode s,UUID p,String hours) throws Exception {
  return json(request(addRequest(s,p,"2026-10-05",hours),"alice").andExpect(status().isCreated()));
 }
 MockHttpServletRequestBuilder submit(JsonNode s) { return post(base(s)+"/submit").header("X-Timesheet-Version",s.get("version").asLong()); }
 @Test void createsDraftAndIndependentEmployeeMonth() throws Exception {
  var a=create("alice"); var b=create("bob");
  assertThat(a.get("status").asText()).isEqualTo("DRAFT"); assertThat(a.get("totalHours").isString()).isTrue(); assertThat(new java.math.BigDecimal(a.get("totalHours").asText())).isEqualByComparingTo("0");
  assertThat(a.get("id")).isNotEqualTo(b.get("id"));
 }
 @Test void duplicateMonthConflicts() throws Exception {
  create("alice");
  request(post("/api/v1/timesheets").contentType("application/json").content("{\"year\":2026,\"month\":10}"),"alice")
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMESHEET_ALREADY_EXISTS"));
 }
 @Test void missingMonthDoesNotCreate() throws Exception {
  request(get("/api/v1/timesheets/2026/10"),"alice").andExpect(status().isNotFound());
  assertThat(sql.queryForObject("SELECT count(*) FROM timesheets",Integer.class)).isZero();
 }
 @Test void decimalEntriesAndDailyTotalsAcrossProjects() throws Exception {
  var s=add(add(create("alice"),project,"6.25"),second,"2.50");
  assertThat(s.get("totalHours").asText()).isEqualTo("8.75");
  assertThat(s.get("dailyTotals").get(0).get("hours").asText()).isEqualTo("8.75");
  assertThat(s.get("entries")).hasSize(2);
 }
 @ParameterizedTest @ValueSource(strings={"0","-1","1.001","100000"})
 void invalidHoursRejected(String hours) throws Exception {
  request(addRequest(create("alice"),project,"2026-10-05",hours),"alice").andExpect(status().isBadRequest());
 }
 @Test void dailyLimitIncludesOtherProjectsAndRollsBack() throws Exception {
  var s=add(create("alice"),project,"23.5");
  request(addRequest(s,second,"2026-10-05","1"),"alice").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DAILY_HOURS_EXCEEDED"));
  assertThat(sql.queryForObject("SELECT total_hours FROM timesheets",java.math.BigDecimal.class)).isEqualByComparingTo("23.5");
  assertThat(sql.queryForObject("SELECT count(*) FROM timesheet_entries",Integer.class)).isEqualTo(1);
 }
 @Test void outsideMonthRejected() throws Exception {
  request(addRequest(create("alice"),project,"2026-11-01","1"),"alice").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_WORK_DATE"));
 }
 @Test void unassignedProjectRejected() throws Exception {
  sql.update("DELETE FROM project_assignments WHERE project_id=?",project);
  request(addRequest(create("alice"),project,"2026-10-05","1"),"alice").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROJECT_NOT_ASSIGNED"));
 }
 @Test void inactiveAssignmentRejected() throws Exception {
  sql.update("UPDATE project_assignments SET active=false WHERE project_id=?",project);
  request(addRequest(create("alice"),project,"2026-10-05","1"),"alice").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROJECT_ASSIGNMENT_INACTIVE"));
 }
 @ParameterizedTest @ValueSource(strings={"2026-09-30","2026-11-01"})
 void assignmentValidityHonored(String boundary) throws Exception {
  if(boundary.startsWith("2026-09")) sql.update("UPDATE project_assignments SET valid_from='2026-09-01',valid_to='2026-09-30' WHERE project_id=?",project);
  else sql.update("UPDATE project_assignments SET valid_to=null,valid_from='2026-11-01' WHERE project_id=?",project);
  request(addRequest(create("alice"),project,"2026-10-05","1"),"alice").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROJECT_NOT_ASSIGNED"));
 }
 @Test void inactiveProjectRejected() throws Exception {
  sql.update("UPDATE projects SET active=false WHERE id=?",project);
  request(addRequest(create("alice"),project,"2026-10-05","1"),"alice").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROJECT_INACTIVE"));
 }
 @Test void ownerEditsAndDeletesWithCorrectTotals() throws Exception {
  var s=add(create("alice"),project,"6.5"); String entry=s.get("entries").get(0).get("id").asText();
  s=json(request(put(base(s)+"/entries/"+entry).header("X-Timesheet-Version",s.get("version").asLong()).contentType("application/json").content(body(second,"2026-10-06","3.25")),"alice").andExpect(status().isOk()));
  assertThat(s.get("totalHours").asText()).isEqualTo("3.25");
  s=json(request(delete(base(s)+"/entries/"+entry).header("X-Timesheet-Version",s.get("version").asLong()),"alice").andExpect(status().isOk()));
  assertThat(s.get("totalHours").asText()).isEqualTo("0.00"); assertThat(s.get("entries")).isEmpty();
 }
 @Test void otherEmployeeCannotReadOrMutate() throws Exception {
  var s=add(create("alice"),project,"1"); String entry=s.get("entries").get(0).get("id").asText();
  request(get("/api/v1/timesheets/2026/10"),"bob").andExpect(status().isNotFound());
  request(get(base(s)+"/available-projects?workDate=2026-10-05"),"bob").andExpect(status().isNotFound());
  request(addRequest(s,project,"2026-10-05","1"),"bob").andExpect(status().isNotFound());
  request(put(base(s)+"/entries/"+entry).header("X-Timesheet-Version",s.get("version").asLong()).contentType("application/json").content(body(project,"2026-10-05","2")),"bob").andExpect(status().isNotFound());
  request(delete(base(s)+"/entries/"+entry).header("X-Timesheet-Version",s.get("version").asLong()),"bob").andExpect(status().isNotFound());
  request(submit(s),"bob").andExpect(status().isNotFound());
 }
 @Test void submittedSheetIsImmutable() throws Exception {
  var s=add(create("alice"),project,"2");
  s=json(request(submit(s),"alice").andExpect(status().isOk()));
  assertThat(s.get("status").asText()).isEqualTo("PENDING_APPROVAL"); assertThat(s.get("submittedAt").isNull()).isFalse();
  String entry=s.get("entries").get(0).get("id").asText();
  request(addRequest(s,project,"2026-10-05","1"),"alice").andExpect(status().isConflict());
  request(put(base(s)+"/entries/"+entry).header("X-Timesheet-Version",s.get("version").asLong()).contentType("application/json").content(body(project,"2026-10-05","1")),"alice").andExpect(status().isConflict());
  request(delete(base(s)+"/entries/"+entry).header("X-Timesheet-Version",s.get("version").asLong()),"alice").andExpect(status().isConflict());
  request(submit(s),"alice").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMESHEET_ALREADY_SUBMITTED"));
  request(get("/api/v1/timesheets"),"alice").andExpect(jsonPath("$.content[0].status").value("PENDING_APPROVAL"));
 }
 @Test void emptySubmissionRejected() throws Exception {
  request(submit(create("alice")),"alice").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMESHEET_EMPTY"));
 }
 @Test void submissionRevalidatesAssignmentsAtomically() throws Exception {
  var s=add(create("alice"),project,"2"); sql.update("UPDATE project_assignments SET active=false WHERE project_id=?",project);
  request(submit(s),"alice").andExpect(status().isBadRequest());
  assertThat(sql.queryForObject("SELECT status FROM timesheets",String.class)).isEqualTo("DRAFT");
  assertThat(sql.queryForObject("SELECT submitted_at FROM timesheets",Object.class)).isNull();
 }
 @Test void availableProjectsAreDateSpecific() throws Exception {
  var s=create("alice"); sql.update("UPDATE project_assignments SET valid_from='2026-10-10' WHERE project_id=?",second);
  request(get(base(s)+"/available-projects?workDate=2026-10-05"),"alice").andExpect(jsonPath("$.length()").value(1));
  request(get(base(s)+"/available-projects?workDate=2026-10-10"),"alice").andExpect(jsonPath("$.length()").value(2));
 }
 @Test void staleVersionCannotOverwriteEvenDescriptionOnlyEdit() throws Exception {
  var old=add(create("alice"),project,"2"); String path=base(old)+"/entries/"+old.get("entries").get(0).get("id").asText();
  var updated=json(request(put(path).header("X-Timesheet-Version",old.get("version").asLong()).contentType("application/json").content(body(project,"2026-10-05","2").replace("Implementation","Revised")),"alice").andExpect(status().isOk()));
  assertThat(updated.get("version").asLong()).isGreaterThan(old.get("version").asLong());
  request(addRequest(old,second,"2026-10-05","1"),"alice").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
 }
 @Test void simultaneousWritesHaveOneWinner() throws Exception {
  var s=create("alice"); var gate=new CyclicBarrier(2);
  try(var executor=Executors.newFixedThreadPool(2)) {
   Callable<Integer> task=()->{gate.await(10,TimeUnit.SECONDS); return request(addRequest(s,project,"2026-10-05","13"),"alice").andReturn().getResponse().getStatus();};
   var results=executor.invokeAll(List.of(task,task)); var codes=new ArrayList<Integer>();
   for(var result:results) codes.add(result.get());
   assertThat(codes).containsExactlyInAnyOrder(201,409);
  }
  assertThat(sql.queryForObject("SELECT total_hours FROM timesheets",java.math.BigDecimal.class)).isEqualByComparingTo("13");
  assertThat(sql.queryForObject("SELECT count(*) FROM timesheet_entries",Integer.class)).isEqualTo(1);
 }
 @Test void missingVersionRejected() throws Exception {
  var s=create("alice"); request(post(base(s)+"/entries").contentType("application/json").content(body(project,"2026-10-05","1")),"alice").andExpect(status().isBadRequest());
 }
 @Test void anonymousRejected() throws Exception { mvc.perform(get("/api/v1/timesheets")).andExpect(status().isUnauthorized()); }
 @Test void missingEmployeeCannotCreate() throws Exception {
  request(post("/api/v1/timesheets").contentType("application/json").content("{\"year\":2026,\"month\":10}"),"no-employee").andExpect(status().isForbidden());
 }
 @Test void inactiveEmployeeCannotWrite() throws Exception {
  var s=create("alice"); sql.update("UPDATE employees SET active=false WHERE id=?",employee);
  request(addRequest(s,project,"2026-10-05","1"),"alice").andExpect(status().isForbidden());
 }
 @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
 @Autowired com.acme.workforce.timesheet.infrastructure.TimesheetRepository sheetRepository;
 @Test void openApiDescribesDecimalStrings() throws Exception {
  mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
   .andExpect(jsonPath("$.components.schemas.EntryView.properties.hours.type").value("string"))
   .andExpect(jsonPath("$.components.schemas.Detail.properties.totalHours.type").value("string"));
 }
 @Test void databaseVersionRejectsTwoTransactionsReadingTheSameVersion() throws Exception {
  UUID id=UUID.fromString(create("alice").get("id").asText());
  var gate=new CyclicBarrier(2);
  try(var executor=Executors.newFixedThreadPool(2)) {
   Callable<String> writer=()->{
    try {
     new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status->{
      var sheet=sheetRepository.findById(id).orElseThrow();
      try { gate.await(10,TimeUnit.SECONDS); } catch(Exception e) { throw new IllegalStateException(e); }
      sheet.recalculate(new java.math.BigDecimal("0.00"));
      sheetRepository.flush();
     });
     return "committed";
    } catch(org.springframework.dao.OptimisticLockingFailureException expected) { return "conflict"; }
   };
   var results=executor.invokeAll(List.of(writer,writer));
   assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder("committed","conflict");
  }
 }
}
