package com.acme.workforce;

import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.organization.application.InvitationTokens;
import com.acme.workforce.notification.application.NotificationWorker;
import com.acme.workforce.notification.domain.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest(properties="app.invitations.signing-key=isolated-test-key-at-least-32-characters") @AutoConfigureMockMvc @Testcontainers
class PhaseElevenAdministrationTest {
 @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);}
 @Autowired MockMvc mvc;@Autowired JdbcTemplate sql;@Autowired ObjectMapper json;@Autowired IdentityService identity;@Autowired InvitationTokens tokens;@Autowired NotificationWorker worker;
 @MockitoBean JwtDecoder decoder;@MockitoBean EmailGateway mail;
 UUID org,other;
 Jwt token(String who){return Jwt.withTokenValue("test").header("alg","RS256").subject(who).claim("email",who+"@example.test").claim("email_verified",true).build();}
 UUID user(String who){return identity.current(token(who)).getId();}
 ResultActions call(MockHttpServletRequestBuilder request,String who,UUID organization,Object body)throws Exception{
  request.with(jwt().jwt(token(who)));if(organization!=null)request.header("X-Organization-Id",organization);if(body!=null)request.contentType("application/json").content(json.writeValueAsString(body));return mvc.perform(request);}
 JsonNode result(ResultActions result,int status)throws Exception{var response=result.andExpect(status().is(status)).andReturn().getResponse();return response.getContentAsString().isBlank()?json.nullNode():json.readTree(response.getContentAsString());}
 UUID id(JsonNode value){return UUID.fromString(value.get("id").asText());}
 @BeforeEach void setup()throws Exception{sql.execute("TRUNCATE organizations,app_users CASCADE");org=id(result(call(post("/api/v1/organizations").header("Idempotency-Key",UUID.randomUUID()),"owner",null,Map.of("name","Alpha","slug","alpha")),201));other=id(result(call(post("/api/v1/organizations").header("Idempotency-Key",UUID.randomUUID()),"other",null,Map.of("name","Beta","slug","beta")),201));reset(mail);}
 UUID member(String who,String role){UUID id=UUID.randomUUID();sql.update("INSERT INTO organization_memberships(id,organization_id,user_id,role,status) VALUES (?,?,?,?,'ACTIVE')",id,org,user(who),role);return id;}
 JsonNode employee(String number,UUID manager,UUID user)throws Exception{var b=new HashMap<String,Object>();b.put("employeeNumber",number);b.put("displayName","Employee "+number);b.put("active",true);b.put("managerId",manager);b.put("userId",user);return result(call(post("/api/v1/admin/employees"),"owner",org,b),201);}
 JsonNode invite(String email,UUID employee,String role)throws Exception{var b=new HashMap<String,Object>();b.put("email",email);b.put("employeeId",employee);b.put("role",role);return result(call(post("/api/v1/admin/invitations"),"owner",org,b),201);}
 JsonNode project(String code)throws Exception{return result(call(post("/api/v1/projects"),"owner",org,Map.of("code",code,"name",code,"active",true)),201);}
 Map<String,Object> edit(JsonNode e,UUID manager){var b=new HashMap<String,Object>();b.put("employeeNumber",e.get("employeeNumber").asText());b.put("displayName","Edited name");b.put("managerId",manager);b.put("version",e.get("version").asLong());return b;}
 @Test void unlinkedEmployeeLifecycleAndReporting()throws Exception{
  var e=employee("E001",null,null);assertThat(e.get("userId").isNull()).isTrue();UUID id=id(e);
  call(get("/api/v1/reports/monthly/employees?year=2026&month=10"),"owner",org,null).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].employeeName").value("Employee E001"));
  var edited=result(call(put("/api/v1/admin/employees/"+id),"owner",org,edit(e,null)),200);assertThat(edited.get("displayName").asText()).isEqualTo("Edited name");
  call(put("/api/v1/admin/employees/"+id),"owner",org,edit(e,null)).andExpect(status().isConflict());
  call(post("/api/v1/admin/employees/"+id+"/status"),"owner",org,Map.of("active",false,"version",1)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
  call(post("/api/v1/admin/employees/"+id+"/status"),"owner",org,Map.of("active",true,"version",2)).andExpect(status().isOk());
  assertThat(sql.queryForObject("SELECT count(*) FROM organization_admin_audit WHERE organization_id=?",Integer.class,org)).isEqualTo(4);
 }
 @Test void scopedNumbersSearchAndPagination()throws Exception{employee("E001",null,null);employee("E002",null,null);call(post("/api/v1/admin/employees"),"owner",org,Map.of("employeeNumber","E001","displayName","duplicate","active",true)).andExpect(status().isConflict());
  call(post("/api/v1/admin/employees"),"other",other,Map.of("employeeNumber","E001","displayName","Other","active",true)).andExpect(status().isCreated());
  call(get("/api/v1/admin/employees?search=E00&size=1&page=1"),"owner",org,null).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].employeeNumber").value("E002"));}
 @ParameterizedTest @ValueSource(strings={"EMPLOYEE","MANAGER","FINANCE"}) void onlyAdminCanUseAdministration(String role)throws Exception{member("limited",role);for(String path:List.of("employees","members","invitations","projects"))call(get("/api/v1/admin/"+path),"limited",org,null).andExpect(status().isForbidden());call(post("/api/v1/admin/employees"),"limited",org,Map.of("employeeNumber","E","displayName","No","active",true)).andExpect(status().isForbidden());
  UUID random=UUID.randomUUID();call(put("/api/v1/admin/members/"+random),"limited",org,Map.of("role","ADMIN","status","ACTIVE","version",0)).andExpect(status().isForbidden());
  call(post("/api/v1/admin/invitations"),"limited",org,Map.of("email","promote@example.test","role","ADMIN")).andExpect(status().isForbidden());
  call(post("/api/v1/admin/invitations/"+random+"/revoke"),"limited",org,null).andExpect(status().isForbidden());
  call(post("/api/v1/admin/projects/"+random+"/status"),"limited",org,Map.of("active",false)).andExpect(status().isForbidden());}
 @Test void anonymousCannotUseAdministrationOrInvitationAcceptance()throws Exception{mvc.perform(get("/api/v1/admin/employees")).andExpect(status().isUnauthorized());mvc.perform(post("/api/v1/invitations/accept")).andExpect(status().isUnauthorized());}
 @Test void crossOrganizationGuessesNeverRevealResources()throws Exception{var e=employee("A",null,null);var p=project("A");
  call(get("/api/v1/admin/employees/"+id(e)),"other",other,null).andExpect(status().isNotFound());call(get("/api/v1/admin/projects/"+id(p)+"/assignments"),"other",other,null).andExpect(status().isNotFound());
  call(post("/api/v1/admin/invitations"),"other",other,Map.of("email","victim@example.test","employeeId",id(e),"role","ADMIN")).andExpect(status().isNotFound());
  call(post("/api/v1/project-assignments"),"other",other,Map.of("employeeId",id(e),"projectId",id(p),"validFrom","2026-01-01","active",true)).andExpect(status().isNotFound());
  call(get("/api/v1/admin/employees"),"owner",other,null).andExpect(status().isForbidden());}
 @Test void selfAndIndirectManagerCyclesAreRejected()throws Exception{var a=employee("A",null,null);var b=employee("B",id(a),null);
  call(put("/api/v1/admin/employees/"+id(a)),"owner",org,edit(a,id(a))).andExpect(status().isConflict());
  call(put("/api/v1/admin/employees/"+id(a)),"owner",org,edit(a,id(b))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MANAGER_CYCLE"));
  assertThat(sql.queryForObject("SELECT manager_id FROM employees WHERE id=?",UUID.class,id(a))).isNull();}
 @Test void managerMustBeActiveAndHaveManagerRoleWhenLinked()throws Exception{member("manager","EMPLOYEE");var m=employee("M",null,user("manager"));call(post("/api/v1/admin/employees"),"owner",org,Map.of("employeeNumber","E","displayName","E","active",true,"managerId",id(m))).andExpect(status().isConflict());}
 @ParameterizedTest @ValueSource(strings={"SUSPENDED","REVOKED","ACTIVE"}) void lastAdministratorCannotLoseAccess(String state)throws Exception{UUID membership=sql.queryForObject("SELECT id FROM organization_memberships WHERE organization_id=?",UUID.class,org);call(put("/api/v1/admin/members/"+membership),"owner",org,Map.of("role","EMPLOYEE","status",state,"version",0)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ACTIVE_ADMIN"));}
 @Test void concurrentAdminDemotionsPreserveOneActiveAdministrator()throws Exception{UUID second=member("second","ADMIN"),first=sql.queryForObject("SELECT id FROM organization_memberships WHERE organization_id=? AND user_id=?",UUID.class,org,user("owner"));
  var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->{start.await();return call(put("/api/v1/admin/members/"+first),"owner",org,Map.of("role","EMPLOYEE","status","ACTIVE","version",0)).andReturn().getResponse().getStatus();});var b=pool.submit(()->{start.await();return call(put("/api/v1/admin/members/"+second),"second",org,Map.of("role","EMPLOYEE","status","ACTIVE","version",0)).andReturn().getResponse().getStatus();});start.countDown();assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}
  assertThat(sql.queryForObject("SELECT count(*) FROM organization_memberships WHERE organization_id=? AND role='ADMIN' AND status='ACTIVE'",Integer.class,org)).isEqualTo(1);}
 @Test void suspendedMembershipImmediatelyLosesAccessAndCanBeExplicitlyReactivated()throws Exception{UUID m=member("member","ADMIN");call(put("/api/v1/admin/members/"+m),"owner",org,Map.of("role","ADMIN","status","SUSPENDED","version",0)).andExpect(status().isOk());call(get("/api/v1/admin/employees"),"member",org,null).andExpect(status().isForbidden());call(put("/api/v1/admin/members/"+m),"owner",org,Map.of("role","EMPLOYEE","status","ACTIVE","version",1)).andExpect(status().isOk());call(get("/api/v1/projects"),"member",org,null).andExpect(status().isOk());call(get("/api/v1/admin/employees"),"member",org,null).andExpect(status().isForbidden());}
 @Test void invitationCreatesMembershipAndLinkExactlyOnceWithoutOrganizationHeader()throws Exception{var e=employee("E",null,null);var i=invite("join@example.test",id(e),"EMPLOYEE");String token=tokens.token(id(i));assertThat(i.has("token")).isFalse();assertThat(i.has("tokenHash")).isFalse();
  for(int n=0;n<2;n++)call(post("/api/v1/invitations/accept"),"join",null,Map.of("token",token)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
  assertThat(sql.queryForObject("SELECT count(*) FROM organization_memberships WHERE user_id=?",Integer.class,user("join"))).isEqualTo(1);assertThat(sql.queryForObject("SELECT user_id FROM employees WHERE id=?",UUID.class,id(e))).isEqualTo(user("join"));assertThat(sql.queryForObject("SELECT count(*) FROM organization_admin_audit WHERE action='INVITATION_ACCEPTED'",Integer.class)).isEqualTo(1);
  call(post("/api/v1/timesheets"),"join",org,Map.of("year",2026,"month",10)).andExpect(status().isCreated());}
 @Test void wrongOrUnverifiedIdentityCannotAccept()throws Exception{var i=invite("right@example.test",null,"FINANCE");String token=tokens.token(id(i));call(post("/api/v1/invitations/accept"),"wrong",null,Map.of("token",token)).andExpect(status().isNotFound());mvc.perform(post("/api/v1/invitations/accept").with(jwt().jwt(j->j.subject("right").claim("email","right@example.test").claim("email_verified",false))).contentType("application/json").content(json.writeValueAsString(Map.of("token",token)))).andExpect(status().isForbidden());call(post("/api/v1/invitations/accept"),"right",null,Map.of("token",token+"invalid")).andExpect(status().isNotFound());}
 @Test void existingMembershipCannotBeElevatedByInvitationAndRevocationIsExplicit()throws Exception{UUID m=member("existing","EMPLOYEE");var i=invite("existing@example.test",null,"ADMIN");call(post("/api/v1/invitations/accept"),"existing",null,Map.of("token",tokens.token(id(i)))).andExpect(status().isConflict());assertThat(sql.queryForObject("SELECT role FROM organization_memberships WHERE id=?",String.class,m)).isEqualTo("EMPLOYEE");
  call(post("/api/v1/admin/invitations/"+id(i)+"/revoke"),"owner",org,null).andExpect(status().isNoContent());call(post("/api/v1/invitations/accept"),"existing",null,Map.of("token",tokens.token(id(i)))).andExpect(status().isConflict());}
 @Test void pendingDuplicateAndExpiredInvitationAreHandledSafely()throws Exception{var i=invite("new@example.test",null,"EMPLOYEE");call(post("/api/v1/admin/invitations"),"owner",org,Map.of("email","NEW@example.test","role","EMPLOYEE")).andExpect(status().isConflict());sql.update("UPDATE organization_invitations SET created_at=now()-interval '8 days',expires_at=now()-interval '1 day' WHERE id=?",id(i));call(post("/api/v1/invitations/accept"),"new",null,Map.of("token",tokens.token(id(i)))).andExpect(status().isConflict());call(get("/api/v1/admin/invitations"),"owner",org,null).andExpect(jsonPath("$.content[0].status").value("EXPIRED"));invite("new@example.test",null,"EMPLOYEE");}
 @Test void concurrentAcceptanceConsumesOnce()throws Exception{var profile=employee("JOIN",null,null);var i=invite("join@example.test",id(profile),"MANAGER");String token=tokens.token(id(i));user("join");var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){Callable<Integer> accept=()->{start.await();return call(post("/api/v1/invitations/accept"),"join",null,Map.of("token",token)).andReturn().getResponse().getStatus();};var a=pool.submit(accept);var b=pool.submit(accept);start.countDown();assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(200);assertThat(b.get(30,TimeUnit.SECONDS)).isEqualTo(200);}assertThat(sql.queryForObject("SELECT count(*) FROM organization_memberships WHERE user_id=?",Integer.class,user("join"))).isEqualTo(1);
 assertThat(sql.queryForObject("SELECT user_id FROM employees WHERE id=?",UUID.class,id(profile))).isEqualTo(user("join"));
 assertThat(sql.queryForObject("SELECT version FROM employees WHERE id=?",Long.class,id(profile))).isEqualTo(1);
 assertThat(sql.queryForObject("SELECT count(*) FROM organization_admin_audit WHERE resource_id=? AND action='INVITATION_ACCEPTED'",Integer.class,id(i))).isEqualTo(1);
 }
 @Test void emailUsesDurableWorkerWithoutPersistingTokenOrHoldingTransaction()throws Exception{var i=invite("recipient@example.test",null,"EMPLOYEE");String token=tokens.token(id(i));assertThat(sql.queryForObject("SELECT token_hash FROM organization_invitations WHERE id=?",String.class,id(i))).isEqualTo(tokens.hash(token));assertThat(sql.queryForObject("SELECT body FROM notifications WHERE invitation_id=?",String.class,id(i))).doesNotContain(token);
  var message=new AtomicReference<EmailMessage>();when(mail.send(any())).thenAnswer(a->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();message.set(a.getArgument(0));return new EmailDeliveryResult(true);});worker.runOnce();assertThat(message.get().recipient()).isEqualTo("recipient@example.test");assertThat(message.get().body()).contains("Alpha","#token="+token,"EMPLOYEE");worker.runOnce();verify(mail,times(1)).send(any());assertThat(sql.queryForObject("SELECT count(*) FROM notification_attempts",Integer.class)).isEqualTo(1);}
 @Test void revokedInvitationIsCancelledBeforeDelivery()throws Exception{var i=invite("recipient@example.test",null,"EMPLOYEE");call(post("/api/v1/admin/invitations/"+id(i)+"/revoke"),"owner",org,null).andExpect(status().isNoContent());worker.runOnce();verifyNoInteractions(mail);assertThat(sql.queryForObject("SELECT status FROM notifications WHERE invitation_id=?",String.class,id(i))).isEqualTo("CANCELLED");}
 @Test void foreignInvitationsMembershipsAndEmployeeManagersAreNotMutable()throws Exception{
  var e=employee("A",null,null);var invitation=invite("join@example.test",id(e),"EMPLOYEE");UUID membership=sql.queryForObject("SELECT id FROM organization_memberships WHERE organization_id=?",UUID.class,org);
  call(post("/api/v1/admin/invitations/"+id(invitation)+"/revoke"),"other",other,null).andExpect(status().isNotFound());
  call(put("/api/v1/admin/members/"+membership),"other",other,Map.of("role","EMPLOYEE","status","REVOKED","version",0)).andExpect(status().isNotFound());
  call(put("/api/v1/admin/employees/"+id(e)),"other",other,edit(e,null)).andExpect(status().isNotFound());
  call(post("/api/v1/admin/employees"),"other",other,Map.of("employeeNumber","B","displayName","B","active",true,"managerId",id(e))).andExpect(status().isNotFound());
  call(get("/api/v1/admin/invitations"),"other",other,null).andExpect(jsonPath("$.totalElements").value(0));
 }
 @Test void accountLinkRequiresActiveLocalMemberAndCannotBeReassigned()throws Exception{
  var e=employee("E",null,null);member("local","EMPLOYEE");
  call(post("/api/v1/admin/employees/"+id(e)+"/link"),"owner",org,Map.of("userId",user("other"),"version",0)).andExpect(status().isNotFound());
  call(post("/api/v1/admin/employees/"+id(e)+"/link"),"owner",org,Map.of("userId",user("local"),"version",0)).andExpect(status().isOk());
  call(post("/api/v1/admin/employees/"+id(e)+"/link"),"owner",org,Map.of("userId",user("owner"),"version",1)).andExpect(status().isConflict());
  assertThatThrownBy(()->sql.update("UPDATE employees SET user_id=? WHERE id=?",user("owner"),id(e))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test void consumedInvitationCannotBeUsedByAnotherSubjectWithSameVerifiedEmail()throws Exception{
  var invitation=invite("shared@example.test",null,"FINANCE");String token=tokens.token(id(invitation));
  call(post("/api/v1/invitations/accept"),"shared",null,Map.of("token",token)).andExpect(status().isOk());
  mvc.perform(post("/api/v1/invitations/accept").with(jwt().jwt(j->j.subject("different").claim("email","shared@example.test").claim("email_verified",true))).contentType("application/json").content(json.writeValueAsString(Map.of("token",token)))).andExpect(status().isConflict());
  assertThat(sql.queryForObject("SELECT count(*) FROM organization_memberships WHERE organization_id=? AND role='FINANCE'",Integer.class,org)).isEqualTo(1);
 }
 @Test void invitationStatusFilterAndAuditImmutability()throws Exception{
  var invitation=invite("old@example.test",null,"EMPLOYEE");call(post("/api/v1/admin/invitations/"+id(invitation)+"/revoke"),"owner",org,null).andExpect(status().isNoContent());invite("pending@example.test",null,"EMPLOYEE");
  call(get("/api/v1/admin/invitations?status=PENDING"),"owner",org,null).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].email").value("pending@example.test"));
  call(get("/api/v1/admin/invitations?status=REVOKED"),"owner",org,null).andExpect(jsonPath("$.totalElements").value(1));
  assertThatThrownBy(()->sql.update("UPDATE organization_admin_audit SET action='CHANGED' WHERE organization_id=?",org)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test void assignmentLifecycleAndProjectArchivePreserveEligibility()throws Exception{member("employee","EMPLOYEE");var e=employee("E",null,user("employee"));var p=project("P");var body=Map.of("employeeId",id(e),"projectId",id(p),"validFrom","2026-10-01","validTo","2026-10-31","active",true);var a=result(call(post("/api/v1/project-assignments"),"owner",org,body),201);
  call(post("/api/v1/project-assignments"),"owner",org,body).andExpect(status().isConflict());call(get("/api/v1/admin/employees/"+id(e)+"/assignments"),"owner",org,null).andExpect(jsonPath("$.totalElements").value(1));
  var sheet=result(call(post("/api/v1/timesheets"),"employee",org,Map.of("year",2026,"month",10)),201);
  call(post("/api/v1/timesheets/"+id(sheet)+"/entries").header("X-Timesheet-Version",0),"employee",org,Map.of("projectId",id(p),"workDate","2026-10-01","hours",6.25)).andExpect(status().isCreated());
  call(post("/api/v1/admin/projects/"+id(p)+"/status"),"owner",org,Map.of("active",false)).andExpect(status().isOk());
  call(post("/api/v1/timesheets/"+id(sheet)+"/entries").header("X-Timesheet-Version",1),"employee",org,Map.of("projectId",id(p),"workDate","2026-10-02","hours",1)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROJECT_INACTIVE"));
  call(put("/api/v1/project-assignments/"+id(a)),"owner",org,Map.of("employeeId",id(e),"projectId",id(p),"validFrom","2026-10-01","active",false)).andExpect(status().isOk());assertThat(sql.queryForObject("SELECT count(*) FROM timesheet_entries WHERE timesheet_id=?",Integer.class,id(sheet))).isEqualTo(1);}
}
