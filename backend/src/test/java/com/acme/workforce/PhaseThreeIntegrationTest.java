package com.acme.workforce;

import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.employee.domain.Employee;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PhaseThreeIntegrationTest {
 @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
  p.add("spring.datasource.url",postgres::getJdbcUrl); p.add("spring.datasource.username",postgres::getUsername); p.add("spring.datasource.password",postgres::getPassword);
 }
 @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate sql;
 @Autowired IdentityService identity; @Autowired EmployeeRepository employees;
 @MockitoBean JwtDecoder decoder;
 UUID employee,manager,other,project;
 @BeforeEach void setup() {
  sql.execute("TRUNCATE app_users,employees,projects,project_assignments,timesheets,timesheet_entries,timesheet_approvals CASCADE");
  manager=employee("manager","MANAGER",null); other=employee("other","MANAGER",null);
  employee=employee("alice","EMPLOYEE",manager); project=UUID.randomUUID();
  sql.update("INSERT INTO projects(id,code,name,active) VALUES (?,'TEST','Test',true)",project);
  sql.update("INSERT INTO project_assignments(id,employee_id,project_id,valid_from,active) VALUES (?,?,?,'2026-01-01',true)",UUID.randomUUID(),employee,project);
 }
 UUID user(String subject,String role) {
  var token=Jwt.withTokenValue("test").header("alg","RS256").subject(subject).claim("name",subject).build();
  var id=identity.current(token).getId(); sql.update("UPDATE app_users SET role=? WHERE id=?",role,id); return id;
 }
 UUID employee(String subject,String role,UUID managerId) { return employees.saveAndFlush(new Employee(user(subject,role),subject,managerId)).getId(); }
 ResultActions request(MockHttpServletRequestBuilder r,String subject) throws Exception {return mvc.perform(r.with(jwt().jwt(j->j.subject(subject))));}
 JsonNode json(ResultActions result) throws Exception {return mapper.readTree(result.andReturn().getResponse().getContentAsString());}
 JsonNode draft() throws Exception {return json(request(post("/api/v1/timesheets").contentType("application/json").content("{\"year\":2026,\"month\":10}"),"alice").andExpect(status().isCreated()));}
 String path(JsonNode s) {return "/api/v1/timesheets/"+s.get("id").asText();}
 String reviewPath(JsonNode s) {return "/api/v1/approvals/timesheets/"+s.get("id").asText();}
 MockHttpServletRequestBuilder mutate(JsonNode s,String action) {return post(path(s)+"/"+action).header("X-Timesheet-Version",s.get("version").asLong());}
 String entryBody(String hours) throws Exception {return mapper.writeValueAsString(Map.of("projectId",project,"workDate","2026-10-05","hours",hours));}
 JsonNode pending() throws Exception {
  var s=json(request(mutate(draft(),"entries").contentType("application/json").content(entryBody("6.5")),"alice").andExpect(status().isCreated()));
  return json(request(mutate(s,"submit"),"alice").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL")));
 }
 ResultActions decide(JsonNode s,String actor,String action,String comment) throws Exception {
  var req=post(reviewPath(s)+"/"+action).header("X-Timesheet-Version",s.get("version").asLong());
  if(comment!=null) req.contentType("application/json").content(mapper.writeValueAsString(Map.of("comment",comment)));
  return request(req,actor);
 }
 JsonNode reject(JsonNode s) throws Exception {return json(decide(s,"manager","reject","Please correct hours").andExpect(status().isOk())).get("timesheet");}
 JsonNode reopen(JsonNode s) throws Exception {return json(request(mutate(s,"reopen"),"alice").andExpect(status().isOk()));}
 @Test void directManagerListsAndReadsPending() throws Exception {
  var s=pending();
  request(get("/api/v1/approvals/pending"),"manager").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].employee.displayName").value("alice"));
  request(get(reviewPath(s)),"manager").andExpect(status().isOk()).andExpect(jsonPath("$.timesheet.entries.length()").value(1));
  request(get("/api/v1/approvals/pending"),"other").andExpect(jsonPath("$.totalElements").value(0));
 }
 @Test void unrelatedManagerCannotReadApproveOrReject() throws Exception {
  var s=pending(); request(get(reviewPath(s)),"other").andExpect(status().isForbidden());
  decide(s,"other","approve",null).andExpect(status().isForbidden());
  decide(s,"other","reject","No").andExpect(status().isForbidden());
 }
 @ParameterizedTest @ValueSource(strings={"EMPLOYEE","FINANCE","ADMIN"})
 void nonManagerRolesCannotReviewEvenWithRelationship(String role) throws Exception {
  var s=pending(); sql.update("UPDATE app_users SET role=? WHERE id=(SELECT user_id FROM employees WHERE id=?)",role,manager);
  request(get("/api/v1/approvals/pending"),"manager").andExpect(status().isForbidden());
  request(get(reviewPath(s)),"manager").andExpect(status().isForbidden());
  decide(s,"manager","approve",null).andExpect(status().isForbidden());
  decide(s,"manager","reject","No").andExpect(status().isForbidden());
 }
 @Test void unauthenticatedRejected() throws Exception {mvc.perform(get("/api/v1/approvals/pending")).andExpect(status().isUnauthorized());}
 @Test void managerNeedsActiveEmployeeProfile() throws Exception {
  user("missing","MANAGER"); request(get("/api/v1/approvals/pending"),"missing").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MANAGER_EMPLOYEE_PROFILE_NOT_FOUND"));
  sql.update("UPDATE employees SET active=false WHERE id=?",manager);
  request(get("/api/v1/approvals/pending"),"manager").andExpect(status().isForbidden());
 }
 @Test void selfReviewIsForbiddenAndDatabaseRejectsSelfManagerLink() throws Exception {
  var s=pending(); sql.update("UPDATE app_users SET role='MANAGER' WHERE id=(SELECT user_id FROM employees WHERE id=?)",employee);
  assertThatThrownBy(()->sql.update("UPDATE employees SET manager_id=id WHERE id=?",employee)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  decide(s,"alice","approve",null).andExpect(status().isForbidden());
  request(get("/api/v1/approvals/pending"),"alice").andExpect(jsonPath("$.totalElements").value(0));
 }
 @Test void approvalCreatesHistoryAndLocksEmployeeWrites() throws Exception {
  var s=pending(); var approved=json(decide(s,"manager","approve","Looks good").andExpect(status().isOk())).get("timesheet");
  assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
  assertThat(approved.get("approvalHistory")).hasSize(1);
  assertThat(approved.get("approvalHistory").get(0).get("managerEmployeeId").asText()).isEqualTo(manager.toString());
  request(mutate(approved,"entries").contentType("application/json").content(entryBody("1")),"alice").andExpect(status().isConflict());
  String entry=approved.get("entries").get(0).get("id").asText();
  request(put(path(approved)+"/entries/"+entry).header("X-Timesheet-Version",approved.get("version").asLong()).contentType("application/json").content(entryBody("1")),"alice").andExpect(status().isConflict());
  request(delete(path(approved)+"/entries/"+entry).header("X-Timesheet-Version",approved.get("version").asLong()),"alice").andExpect(status().isConflict());
  request(mutate(approved,"reopen"),"alice").andExpect(status().isConflict());
  decide(approved,"manager","approve",null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMESHEET_ALREADY_APPROVED"));
  request(get(reviewPath(s)),"manager").andExpect(jsonPath("$.timesheet.approvalHistory.length()").value(1));
 }
 @Test void approvalCommentIsOptional() throws Exception {decide(pending(),"manager","approve",null).andExpect(status().isOk());}
 @ParameterizedTest @ValueSource(strings={""," ","\t\n"})
 void rejectionRequiresNonBlankComment(String comment) throws Exception {
  decide(pending(),"manager","reject",comment).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REJECTION_COMMENT_REQUIRED"));
  assertThat(sql.queryForObject("SELECT count(*) FROM timesheet_approvals",Integer.class)).isZero();
  assertThat(sql.queryForObject("SELECT status FROM timesheets",String.class)).isEqualTo("PENDING_APPROVAL");
 }
 @Test void rejectionRequiresBodyComment() throws Exception {decide(pending(),"manager","reject",null).andExpect(status().isBadRequest());}
 @Test void overlongCommentRejected() throws Exception {decide(pending(),"manager","reject","x".repeat(2001)).andExpect(status().isBadRequest());}
 @Test void employeeSeesRejectionReasonAndMustExplicitlyReopen() throws Exception {
  var rejected=reject(pending());
  request(get("/api/v1/timesheets/2026/10"),"alice").andExpect(jsonPath("$.status").value("REJECTED")).andExpect(jsonPath("$.approvalHistory[0].comment").value("Please correct hours")).andExpect(jsonPath("$.approvalHistory[0].decidedAt").isNotEmpty());
  request(mutate(rejected,"entries").contentType("application/json").content(entryBody("1")),"alice").andExpect(status().isConflict());
  request(mutate(rejected,"submit"),"alice").andExpect(status().isConflict());
  var draft=reopen(rejected); assertThat(draft.get("status").asText()).isEqualTo("DRAFT"); assertThat(draft.get("submittedAt").isNull()).isTrue();
  String id=draft.get("entries").get(0).get("id").asText();
  var edited=json(request(put(path(draft)+"/entries/"+id).header("X-Timesheet-Version",draft.get("version").asLong()).contentType("application/json").content(entryBody("7.25")),"alice").andExpect(status().isOk()));
  var resubmitted=json(request(mutate(edited,"submit"),"alice").andExpect(status().isOk()));
  assertThat(resubmitted.get("status").asText()).isEqualTo("PENDING_APPROVAL"); assertThat(resubmitted.get("approvalHistory")).hasSize(1);
 }
 @Test void repeatedDecisionCyclesPreserveAllHistory() throws Exception {
  var s=pending();
  for(int i=0;i<2;i++) s=json(request(mutate(reopen(reject(s)),"submit"),"alice").andExpect(status().isOk()));
  var approved=json(decide(s,"manager","approve",null).andExpect(status().isOk())).get("timesheet");
  assertThat(approved.get("approvalHistory")).hasSize(3);
  assertThat(approved.get("approvalHistory").get(0).get("decision").asText()).isEqualTo("REJECTED");
  assertThat(approved.get("approvalHistory").get(2).get("decision").asText()).isEqualTo("APPROVED");
 }
 @Test void staleDecisionAndReopenReturnConflictWithoutExtraHistory() throws Exception {
  var original=pending(); var rejected=reject(original);
  decide(original,"manager","approve",null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
  request(mutate(original,"reopen"),"alice").andExpect(status().isConflict());
  reopen(rejected); request(mutate(rejected,"reopen"),"alice").andExpect(status().isConflict());
  assertThat(sql.queryForObject("SELECT count(*) FROM timesheet_approvals",Integer.class)).isEqualTo(1);
 }
 @Test void managerCannotDecideDraftAndEmployeeCannotReopenPendingOrOthers() throws Exception {
  var draft=draft(); decide(draft,"manager","approve",null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMESHEET_NOT_PENDING_APPROVAL"));
  request(mutate(draft,"reopen"),"alice").andExpect(status().isConflict());
  request(mutate(draft,"reopen"),"other").andExpect(status().isNotFound());
 }
 @Test void simultaneousDecisionsCreateOnlyOneHistoryRow() throws Exception {
  var s=pending(); var gate=new CyclicBarrier(2);
  try(var executor=Executors.newFixedThreadPool(2)) {
   Callable<Integer> action=()->{gate.await(10,TimeUnit.SECONDS);return decide(s,"manager","approve",null).andReturn().getResponse().getStatus();};
   var results=executor.invokeAll(List.of(action,action));
   assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,409);
  }
  assertThat(sql.queryForObject("SELECT count(*) FROM timesheet_approvals",Integer.class)).isEqualTo(1);
 }
 @Test void approvalHistoryCannotBeUpdatedOrDeletedInDatabase() throws Exception {
  reject(pending());
  assertThatThrownBy(()->sql.update("UPDATE timesheet_approvals SET comment='changed'")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->sql.update("DELETE FROM timesheet_approvals")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test void submittedPhaseTwoDataMigratesWithoutInventingDecision() {
  org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas("legacy").defaultSchema("legacy").target("6").load().migrate();
  UUID u=UUID.randomUUID(),e=UUID.randomUUID(),t=UUID.randomUUID();
  sql.update("INSERT INTO legacy.app_users(id,external_subject,email,display_name,role) VALUES (?,'legacy','','Legacy','EMPLOYEE')",u);
  sql.update("INSERT INTO legacy.employees(id,user_id,employee_number,active) VALUES (?,?,'LEGACY',true)",e,u);
  sql.update("INSERT INTO legacy.timesheets(id,employee_id,year,month,status,submitted_at,version) VALUES (?,?,2026,9,'SUBMITTED','2026-09-30T12:00:00Z',3)",t,e);
  org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas("legacy").defaultSchema("legacy").load().migrate();
  assertThat(sql.queryForObject("SELECT status FROM legacy.timesheets",String.class)).isEqualTo("PENDING_APPROVAL");
  assertThat(sql.queryForObject("SELECT version FROM legacy.timesheets",Long.class)).isEqualTo(4);
  assertThat(sql.queryForObject("SELECT count(*) FROM legacy.timesheet_approvals",Integer.class)).isZero();
  assertThat(sql.queryForObject("SELECT submitted_at::text FROM legacy.timesheets",String.class)).startsWith("2026-09-30 12:00:00");
 }
}
