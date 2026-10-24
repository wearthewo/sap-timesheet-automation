package com.acme.workforce;

import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.employee.domain.Employee;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import com.acme.workforce.sap.application.SapExportWorker;
import com.acme.workforce.sap.domain.*;
import com.acme.workforce.sap.infrastructure.SapJobStore;
import com.acme.workforce.notification.application.NotificationIntents;
import com.acme.workforce.notification.infrastructure.NotificationStore;
import com.acme.workforce.notification.domain.NotificationType;
import com.acme.workforce.notification.application.NotificationTemplates;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PhaseNineIsolationTest {
 @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
  p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);
 }
 @Autowired MockMvc mvc; @Autowired JdbcTemplate sql; @Autowired IdentityService identity; @Autowired EmployeeRepository employees;
 @Autowired SapJobStore jobs; @Autowired SapExportWorker worker; @Autowired NotificationStore notifications; @Autowired NotificationIntents intents;
 @Autowired PlatformTransactionManager tm;
 @MockitoBean JwtDecoder decoder; @MockitoBean SapTimesheetGateway gateway;
 UUID a,b,shared,financeA,financeB,managerA,managerB,employeeA,employeeB,projectA,projectB,sheetA,sheetB;
 UUID organization(String slug) {
  var id=UUID.randomUUID();sql.update("INSERT INTO organizations(id,slug,name,status) VALUES (?,?,?,'ACTIVE')",id,slug,slug);return id;
 }
 UUID user(String sub) { return identity.current(Jwt.withTokenValue("test").header("alg","RS256").claim("email_verified",true).subject(sub).claim("name",sub).claim("email",sub+"@example.test").build()).getId(); }
 void membership(UUID org,UUID user,String role) { sql.update("INSERT INTO organization_memberships(id,organization_id,user_id,role,status) VALUES (?,?,?,?,'ACTIVE')",UUID.randomUUID(),org,user,role); }
 UUID project(UUID org) {var id=UUID.randomUUID();sql.update("INSERT INTO projects(id,organization_id,code,name) VALUES (?,?,'SAME-CODE','Same business code')",id,org);return id;}
 UUID sheet(UUID employee,UUID project) {
  var id=UUID.randomUUID();sql.update("INSERT INTO timesheets(id,employee_id,year,month,status,total_hours,submitted_at) VALUES (?,?,2026,10,'APPROVED',6.25,now())",id,employee);
  sql.update("INSERT INTO timesheet_entries(id,timesheet_id,project_id,work_date,hours) VALUES (?,?,?,'2026-10-05',6.25)",UUID.randomUUID(),id,project);return id;
 }
 @BeforeEach void seed() {
  sql.execute("TRUNCATE organizations,app_users,projects CASCADE");
  a=organization("alpha");b=organization("beta");shared=user("shared");membership(a,shared,"ADMIN");membership(b,shared,"EMPLOYEE");
  financeA=user("finance-a");membership(a,financeA,"FINANCE");financeB=user("finance-b");membership(b,financeB,"FINANCE");
  var ma=user("manager-a");membership(a,ma,"MANAGER");var mb=user("manager-b");membership(b,mb,"MANAGER");
  managerA=employees.saveAndFlush(new Employee(a,ma,"M001",null)).getId();managerB=employees.saveAndFlush(new Employee(b,mb,"M001",null)).getId();
  employeeA=employees.saveAndFlush(new Employee(a,shared,"E001",managerA)).getId();employeeB=employees.saveAndFlush(new Employee(b,shared,"E001",managerB)).getId();
  projectA=project(a);projectB=project(b);sheetA=sheet(employeeA,projectA);sheetB=sheet(employeeB,projectB);
  when(gateway.export(any())).thenAnswer(i->new SapExportResult("SAP-"+((ApprovedTimesheetPayload)i.getArgument(0)).timesheetId()));
 }
 ResultActions request(MockHttpServletRequestBuilder r,UUID org,String user) throws Exception {return mvc.perform(r.header("X-Organization-Id",org).with(jwt().jwt(j->j.claim("email_verified",true).subject(user).claim("role","ADMIN"))));}
 ResultActions enqueue(UUID org,UUID sheet,String user) throws Exception {return request(post("/api/v1/sap/timesheets/"+sheet+"/export").header("X-Timesheet-Version",0),org,user);}
 @Test void rolesAreOrganizationSpecificAndIdentityClaimsCannotElevate() throws Exception {
  request(get("/api/v1/auth/me"),a,"shared").andExpect(jsonPath("$.role").value("ADMIN"));
  request(get("/api/v1/auth/me"),b,"shared").andExpect(jsonPath("$.role").value("EMPLOYEE"));
  request(post("/api/v1/projects").contentType("application/json").content("{\"code\":\"NEW\",\"name\":\"New\",\"active\":true}"),b,"shared").andExpect(status().isForbidden());
  assertThat(sql.queryForObject("SELECT role FROM app_users WHERE id=?",String.class,shared)).isEqualTo("EMPLOYEE");
 }
 @Test void dashboardAdminMetricsStayWithinOrganization() throws Exception {
  sql.update("UPDATE projects SET active=false WHERE id=?",projectB);
  request(get("/api/v1/dashboard"),a,"shared").andExpect(status().isOk())
   .andExpect(jsonPath("$.administration.activeEmployees").value(2))
   .andExpect(jsonPath("$.administration.activeProjects").value(1))
   .andExpect(jsonPath("$.administration.pendingInvitations").value(0))
   .andExpect(jsonPath("$.finance.awaitingExport").value(1)).andExpect(jsonPath("$.management").doesNotExist());
  request(get("/api/v1/dashboard"),b,"shared").andExpect(status().isOk())
   .andExpect(jsonPath("$.role").value("EMPLOYEE")).andExpect(jsonPath("$.administration").doesNotExist())
   .andExpect(jsonPath("$.finance").doesNotExist()).andExpect(jsonPath("$.management").doesNotExist());
 }
 @Test void dashboardFinanceCannotSeeAdministrationOrOtherTenantExports() throws Exception {
  enqueue(b,sheetB,"finance-b");
  request(get("/api/v1/dashboard"),a,"finance-a").andExpect(status().isOk())
   .andExpect(jsonPath("$.finance.awaitingExport").value(1)).andExpect(jsonPath("$.finance.queued").value(0))
   .andExpect(jsonPath("$.administration").doesNotExist()).andExpect(jsonPath("$.management").doesNotExist());
  request(get("/api/v1/dashboard"),b,"finance-b").andExpect(jsonPath("$.finance.queued").value(1));
 }
 @Test void dashboardManagerSeesOnlyCurrentDirectReports() throws Exception {
  sql.update("UPDATE timesheets SET status='PENDING_APPROVAL'");
  request(get("/api/v1/dashboard"),a,"manager-a").andExpect(jsonPath("$.management.pendingApprovals").value(1))
   .andExpect(jsonPath("$.finance").doesNotExist()).andExpect(jsonPath("$.administration").doesNotExist());
  request(post("/api/v1/approvals/timesheets/"+sheetA+"/approve").header("X-Timesheet-Version",0).contentType("application/json").content("{}"),a,"manager-a").andExpect(status().isOk());
  request(get("/api/v1/dashboard"),a,"manager-a").andExpect(jsonPath("$.management.pendingApprovals").value(0))
   .andExpect(jsonPath("$.management.recentDecisions[0].timesheetId").value(sheetA.toString()));
  sql.update("UPDATE employees SET manager_id=NULL WHERE id=?",employeeA);
  request(get("/api/v1/dashboard"),a,"manager-a").andExpect(jsonPath("$.management.recentDecisions").isEmpty());
 }
 @Test void dashboardInactiveManagerCannotReadTeamMetrics() throws Exception {
  sql.update("UPDATE employees SET active=false WHERE id=?",managerA);
  request(get("/api/v1/dashboard"),a,"manager-a").andExpect(status().isOk()).andExpect(jsonPath("$.management").doesNotExist());
 }
 @Test void dashboardRejectedMonthsAreOwnedAndBounded() throws Exception {
  sql.update("UPDATE timesheets SET status='REJECTED'");
  for(int month=1;month<=6;month++) sql.update("INSERT INTO timesheets(id,employee_id,year,month,status,total_hours,submitted_at) VALUES (?,?,2025,?,'REJECTED',0,now())",UUID.randomUUID(),employeeB,month);
  request(get("/api/v1/dashboard"),b,"shared").andExpect(jsonPath("$.rejectedMonths.length()").value(5))
   .andExpect(jsonPath("$.rejectedMonths[0].year").value(2026)).andExpect(jsonPath("$.rejectedMonths[1].month").value(6));
  request(get("/api/v1/dashboard"),a,"shared").andExpect(jsonPath("$.rejectedMonths.length()").value(1));
 }
 @Test void dashboardRejectsMissingForeignAndSuspendedMembership() throws Exception {
  mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/dashboard").with(jwt().jwt(j->j.subject("shared").claim("email_verified",true)))).andExpect(status().isBadRequest());
  request(get("/api/v1/dashboard"),b,"finance-a").andExpect(status().isForbidden());
  sql.update("UPDATE organization_memberships SET status='SUSPENDED' WHERE organization_id=? AND user_id=?",a,financeA);
  request(get("/api/v1/dashboard"),a,"finance-a").andExpect(status().isForbidden());
 }
 @Test void dashboardEmptyOrganizationHasRealZeroCounts() throws Exception {
  var empty=organization("empty");membership(empty,shared,"ADMIN");
  request(get("/api/v1/dashboard"),empty,"shared").andExpect(jsonPath("$.administration.activeEmployees").value(0))
   .andExpect(jsonPath("$.administration.activeProjects").value(0)).andExpect(jsonPath("$.finance.awaitingExport").value(0))
   .andExpect(jsonPath("$.rejectedMonths").isEmpty());
 }
 @Test void dashboardRoleChangeRemovesFinanceProjectionImmediately() throws Exception {
  request(get("/api/v1/dashboard"),a,"finance-a").andExpect(jsonPath("$.finance").exists());
  sql.update("UPDATE organization_memberships SET role='EMPLOYEE' WHERE organization_id=? AND user_id=?",a,financeA);
  request(get("/api/v1/dashboard"),a,"finance-a").andExpect(jsonPath("$.finance").doesNotExist());
 }
 @Test void dashboardCountsAllSapStatesWithoutMixingUnapprovedSheets() throws Exception {
  int month=1;
  for(var state:List.of("QUEUED","PROCESSING","SUCCEEDED","FAILED","UNKNOWN")) {
   var id=UUID.randomUUID();
   sql.update("INSERT INTO timesheets(id,employee_id,year,month,status,total_hours,submitted_at) VALUES (?,?,2027,?,'APPROVED',0,now())",id,employeeA,month++);
   boolean succeeded=state.equals("SUCCEEDED"), failed=Set.of("FAILED","UNKNOWN").contains(state);
   sql.update("INSERT INTO sap_timesheet_exports(id,timesheet_id,status,external_reference,exported_at,failure_code,failure_message,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
    UUID.randomUUID(),id,state,succeeded?"SAP-DASHBOARD":null,succeeded?java.sql.Timestamp.from(java.time.Instant.now()):null,
    failed?"SAFE_CODE":null,failed?"Safe message":null,java.sql.Timestamp.from(java.time.Instant.now()),java.sql.Timestamp.from(java.time.Instant.now()));
  }
  sql.update("INSERT INTO timesheets(id,employee_id,year,month,status,total_hours) VALUES (?,?,2027,12,'DRAFT',0)",UUID.randomUUID(),employeeA);
  request(get("/api/v1/dashboard"),a,"finance-a").andExpect(jsonPath("$.finance.awaitingExport").value(1))
   .andExpect(jsonPath("$.finance.queued").value(1)).andExpect(jsonPath("$.finance.processing").value(1))
   .andExpect(jsonPath("$.finance.succeeded").value(1)).andExpect(jsonPath("$.finance.failed").value(1)).andExpect(jsonPath("$.finance.unknown").value(1));
 }
 @Test void dashboardCalendarUsesConfiguredAthensClockAtUtcMonthBoundary() {
  var access=mock(com.acme.workforce.organization.application.OrganizationAccess.class);
  when(access.id()).thenReturn(a);when(access.role(shared)).thenReturn(com.acme.workforce.identity.domain.UserRole.ADMIN);
  var service=new com.acme.workforce.dashboard.application.DashboardService(sql,access,
   java.time.Clock.fixed(java.time.Instant.parse("2028-02-29T22:30:00Z"),java.time.ZoneOffset.UTC),"Europe/Athens");
  assertThat(service.summary(shared).businessDate()).isEqualTo("2028-03-01");
  assertThat(service.summary(shared).timezone()).isEqualTo("Europe/Athens");
 }
 @Test void membershipListingDoesNotExposeOtherOrganizations() throws Exception {
  mvc.perform(get("/api/v1/auth/organizations").with(jwt().jwt(j->j.claim("email_verified",true).subject("finance-a")))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(a.toString()));
 }
 @Test void unknownUserIsProvisionedWithoutAnyMembership() throws Exception {
  mvc.perform(get("/api/v1/auth/organizations").with(jwt().jwt(j->j.claim("email_verified",true).subject("new-user")))).andExpect(status().isOk()).andExpect(content().json("[]"));
  request(get("/api/v1/projects"),a,"new-user").andExpect(status().isForbidden());
 }
 @Test void missingOrganizationAndAnonymousRequestsAreRejected() throws Exception {
  mvc.perform(get("/api/v1/projects").with(jwt().jwt(j->j.claim("email_verified",true).subject("shared")))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/projects").header("X-Organization-Id",a)).andExpect(status().isUnauthorized());
 }
 @Test void membershipSuspensionAndRoleChangeTakeEffectOnNextRequest() throws Exception {
  request(get("/api/v1/reports/monthly/summary?year=2026&month=10"),a,"finance-a").andExpect(status().isOk());
  sql.update("UPDATE organization_memberships SET role='EMPLOYEE' WHERE organization_id=? AND user_id=?",a,financeA);
  request(get("/api/v1/reports/monthly/summary?year=2026&month=10"),a,"finance-a").andExpect(status().isForbidden());
  sql.update("UPDATE organization_memberships SET status='SUSPENDED' WHERE organization_id=? AND user_id=?",a,financeA);
  request(get("/api/v1/projects"),a,"finance-a").andExpect(status().isForbidden());
 }
 @Test void organizationSuspensionBlocksAllBusinessAccess() throws Exception {
  sql.update("UPDATE organizations SET status='SUSPENDED' WHERE id=?",a);
  request(get("/api/v1/projects"),a,"shared").andExpect(status().isForbidden());
 }
 @Test void adminInOneOrganizationHasNoMembershipBypass() throws Exception {
  sql.update("UPDATE app_users SET role='ADMIN' WHERE id=?",financeA);
  request(get("/api/v1/projects"),b,"finance-a").andExpect(status().isForbidden());
 }
 @Test void projectsAndOwnTimesheetsResolveWithinSelectedOrganization() throws Exception {
  request(get("/api/v1/projects"),a,"shared").andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(projectA.toString()));
  request(get("/api/v1/timesheets/2026/10"),b,"shared").andExpect(jsonPath("$.id").value(sheetB.toString()));
  request(get("/api/v1/projects/"+projectB),a,"shared").andExpect(status().isNotFound());
  request(put("/api/v1/projects/"+projectB).contentType("application/json").content("{\"code\":\"CHANGED\",\"name\":\"Wrong\",\"active\":true}"),a,"shared").andExpect(status().isNotFound());
 }
 @Test void crossOrganizationAssignmentIsRejectedByApiAndDatabase() throws Exception {
  request(post("/api/v1/project-assignments").contentType("application/json").content("{\"employeeId\":\""+employeeB+"\",\"projectId\":\""+projectA+"\",\"validFrom\":\"2026-01-01\",\"active\":true}"),a,"shared").andExpect(status().isNotFound());
  assertThatThrownBy(()->sql.update("INSERT INTO project_assignments(id,employee_id,project_id,valid_from) VALUES (?,?,?,'2026-01-01')",UUID.randomUUID(),employeeA,projectB)).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->sql.update("UPDATE employees SET manager_id=? WHERE id=?",managerB,employeeA)).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->sql.update("INSERT INTO timesheet_entries(id,timesheet_id,project_id,work_date,hours) VALUES (?,?,?,'2026-10-06',1)",UUID.randomUUID(),sheetA,projectB)).isInstanceOf(DataIntegrityViolationException.class);
 }
 @ParameterizedTest @ValueSource(strings={"","/export","/export.xlsx","/export/attempts"})
 void financeCannotGuessCrossTenantSapResources(String suffix) throws Exception {
  request(get("/api/v1/sap/timesheets/"+sheetB+suffix),a,"finance-a").andExpect(status().isNotFound());
 }
 @ParameterizedTest @ValueSource(strings={"/export","/export/retry","/export/reconcile"})
 void financeCannotMutateOtherTenantExports(String suffix) throws Exception {
  request(post("/api/v1/sap/timesheets/"+sheetB+suffix).header("X-Timesheet-Version",0),a,"finance-a").andExpect(status().isNotFound());
  assertThat(sql.queryForObject("SELECT count(*) FROM sap_export_jobs",Long.class)).isZero();
 }
 @Test void managersAreLimitedToTheirOrganizationAndDirectReports() throws Exception {
  sql.update("UPDATE timesheets SET status='PENDING_APPROVAL'");
  request(get("/api/v1/approvals/pending"),a,"manager-a").andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].timesheet.id").value(sheetA.toString()));
  request(get("/api/v1/approvals/timesheets/"+sheetB),a,"manager-a").andExpect(status().isNotFound());
  request(post("/api/v1/approvals/timesheets/"+sheetB+"/approve").header("X-Timesheet-Version",0),a,"manager-a").andExpect(status().isNotFound());
  request(post("/api/v1/approvals/timesheets/"+sheetA+"/approve").header("X-Timesheet-Version",0),a,"shared").andExpect(status().isForbidden());
 }
 @Test void financeReportsAndDownloadsUseSelectedOrganizationOnly() throws Exception {
  request(get("/api/v1/reports/monthly/summary?year=2026&month=10"),a,"finance-a").andExpect(status().isOk()).andExpect(jsonPath("$.totalApprovedHours").value("6.25"));
  sql.update("UPDATE employees SET employee_number='PRIVATE-B' WHERE id=?",employeeB);
  String csv=request(get("/api/v1/reports/monthly/approved-hours.csv?year=2026&month=10"),a,"finance-a").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(csv).contains("E001").doesNotContain("PRIVATE-B");
 }
 @Test void workerClaimsCarryOwnershipAndResultsCannotMixOrganizations() throws Exception {
  enqueue(a,sheetA,"finance-a").andExpect(status().isAccepted());enqueue(b,sheetB,"finance-b").andExpect(status().isAccepted());
  var claims=jobs.claim("tenant-test");assertThat(claims).hasSize(2);
  for(var c:claims)assertThat(c.organizationId()).isEqualTo(c.timesheetId().equals(sheetA)?a:b);
  claims.forEach(worker::process);
  assertThat(sql.queryForObject("SELECT count(*) FROM sap_timesheet_exports WHERE status='SUCCEEDED'",Long.class)).isEqualTo(2);
  assertThat(sql.queryForObject("SELECT count(*) FROM sap_export_attempts a JOIN sap_export_jobs j ON j.id=a.job_id WHERE a.organization_id<>j.organization_id",Long.class)).isZero();
  assertThat(jobs.claim("again")).isEmpty();
 }
 @Test void multipleWorkersCannotClaimSameJobAcrossOrganizations() throws Exception {
  enqueue(a,sheetA,"finance-a");enqueue(b,sheetB,"finance-b");
  try(var pool=Executors.newFixedThreadPool(2)) {
   var barrier=new CyclicBarrier(2);
   var results=pool.invokeAll(List.<Callable<List<SapJobStore.Claim>>>of(()->{barrier.await();return jobs.claim("one");},()->{barrier.await();return jobs.claim("two");}));
   var ids=new ArrayList<UUID>();for(var result:results)for(var c:result.get())ids.add(c.id());
   assertThat(ids).hasSize(2).doesNotHaveDuplicates();
  }
 }
 @Test void sameUserNotificationHistoryAndFinanceRecipientsStaySeparate() throws Exception {
  new TransactionTemplate(tm).executeWithoutResult(s->{
   notifications.enqueue(NotificationType.TIMESHEET_APPROVED,new NotificationStore.Recipient(a,shared,"shared@example.test","Shared"),"same-key",new NotificationTemplates.Content("Alpha","ALPHA PRIVATE"));
   notifications.enqueue(NotificationType.TIMESHEET_APPROVED,new NotificationStore.Recipient(b,shared,"shared@example.test","Shared"),"same-key",new NotificationTemplates.Content("Beta","BETA PRIVATE"));
  });
  var response=request(get("/api/v1/notifications"),a,"shared").andExpect(jsonPath("$.totalElements").value(1)).andReturn().getResponse().getContentAsString();
  assertThat(response).contains("ALPHA PRIVATE").doesNotContain("BETA PRIVATE");
  assertThat(notifications.financeRecipients(a)).extracting(NotificationStore.Recipient::id).containsExactlyInAnyOrder(shared,financeA);
  assertThat(notifications.financeRecipients(b)).extracting(NotificationStore.Recipient::id).containsExactly(financeB);
  var claims=notifications.claim("two-tenants");assertThat(claims).extracting(NotificationStore.Claim::organizationId).containsExactlyInAnyOrder(a,b);
 }
 @Test void suspendedOrganizationJobsAreNotClaimed() throws Exception {
  enqueue(a,sheetA,"finance-a");enqueue(b,sheetB,"finance-b");
  sql.update("UPDATE organizations SET status='SUSPENDED' WHERE id=?",a);
  assertThat(jobs.claim("active-only")).extracting(SapJobStore.Claim::organizationId).containsExactly(b);
 }
 @Test void legacyMigrationRequiresExplicitOwnershipDecisionAndPreservesIdentity() {
  var schema="legacy_guard";
  var config=org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas(schema).defaultSchema(schema);
  config.target("14").load().migrate();
  UUID user=UUID.randomUUID(),employee=UUID.randomUUID();
  sql.update("INSERT INTO legacy_guard.app_users(id,external_subject,email,display_name,role) VALUES (?,'legacy-finance','legacy@example.test','Legacy Person','FINANCE')",user);
  sql.update("INSERT INTO legacy_guard.employees(id,user_id,employee_number) VALUES (?,?,'LEGACY')",employee,user);
  UUID sheet=UUID.randomUUID(),export=UUID.randomUUID(),job=UUID.randomUUID(),notification=UUID.randomUUID();
  sql.update("INSERT INTO legacy_guard.timesheets(id,employee_id,year,month,status,total_hours,submitted_at) VALUES (?,?,2026,10,'APPROVED',1.25,now())",sheet,employee);
  sql.update("INSERT INTO legacy_guard.sap_timesheet_exports(id,timesheet_id,status,external_reference,exported_at,created_at,updated_at) VALUES (?,?,'SUCCEEDED','SAP-LEGACY',now(),now(),now())",export,sheet);
  sql.update("INSERT INTO legacy_guard.sap_export_jobs(id,export_id,timesheet_id,status,operation,payload,next_attempt_at) VALUES (?,?,?,'COMPLETED','EXPORT',jsonb_build_object('timesheetId',?::text,'employeeNumber','LEGACY','year',2026,'month',10,'entries',jsonb_build_array(jsonb_build_object('projectCode','HISTORICAL','workDate','2026-10-05','hours',1.25,'description','Original export'))),now())",job,export,sheet,sheet);
  sql.update("INSERT INTO legacy_guard.sap_export_attempts(id,export_id,job_id,attempt_number,operation,started_at,finished_at,outcome,duration_ms,external_reference) VALUES (?,?,?,1,'EXPORT',now(),now(),'SUCCEEDED',0,'SAP-LEGACY')",UUID.randomUUID(),export,job);
  sql.update("INSERT INTO legacy_guard.notifications(id,type,recipient_user_id,recipient_email,subject,body,status,deduplication_key,sent_at,attempt_count) VALUES (?,'TIMESHEET_APPROVED',?,'legacy@example.test','Original subject','Original body','SENT','legacy-message',now(),1)",notification,user);
  sql.update("INSERT INTO legacy_guard.notification_attempts(id,notification_id,attempt_number,started_at,finished_at,outcome,duration_ms) VALUES (?,?,1,now(),now(),'SENT',0)",UUID.randomUUID(),notification);
  String snapshot=sql.queryForObject("SELECT payload::text FROM legacy_guard.sap_export_jobs WHERE id=?",String.class,job);
  assertThatThrownBy(()->org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas(schema).defaultSchema(schema).placeholders(Map.of("legacyTenantConfirmed","false")).load().migrate())
   .hasMessageContaining("Review legacy ownership");
  assertThat(sql.queryForObject("SELECT user_id FROM legacy_guard.employees WHERE id=?",UUID.class,employee)).isEqualTo(user);
  org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas(schema).defaultSchema(schema).placeholders(Map.of("legacyTenantConfirmed","true")).load().migrate();
  assertThat(sql.queryForObject("SELECT role FROM legacy_guard.organization_memberships WHERE user_id=?",String.class,user)).isEqualTo("FINANCE");
  assertThat(sql.queryForObject("SELECT organization_id FROM legacy_guard.employees WHERE id=?",UUID.class,employee)).isEqualTo(TenantFixtures.ORGANIZATION);
  assertThat(sql.queryForObject("SELECT payload::text FROM legacy_guard.sap_export_jobs WHERE id=?",String.class,job)).isEqualTo(snapshot);
  assertThat(sql.queryForObject("SELECT external_reference FROM legacy_guard.sap_timesheet_exports WHERE id=?",String.class,export)).isEqualTo("SAP-LEGACY");
  assertThat(sql.queryForObject("SELECT body FROM legacy_guard.notifications WHERE id=?",String.class,notification)).isEqualTo("Original body");
  for(String table:List.of("sap_export_attempts","notification_attempts")) {
   assertThat(sql.queryForObject("SELECT organization_id FROM legacy_guard."+table,UUID.class)).isEqualTo(TenantFixtures.ORGANIZATION);
   assertThatThrownBy(()->sql.update("DELETE FROM legacy_guard."+table)).isInstanceOf(DataIntegrityViolationException.class);
  }
 }
}
