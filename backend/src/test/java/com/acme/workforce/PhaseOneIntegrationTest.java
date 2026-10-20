package com.acme.workforce;

import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.identity.infrastructure.UserRepository;
import com.acme.workforce.employee.domain.Employee;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import com.acme.workforce.common.api.ExceptionHandlerAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PhaseOneIntegrationTest extends TenantFixtures {
 @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
 @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
  properties.add("spring.datasource.url",postgres::getJdbcUrl);
  properties.add("spring.datasource.username",postgres::getUsername);
  properties.add("spring.datasource.password",postgres::getPassword);
 }
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper mapper;
 @Autowired JdbcTemplate sql;
 @Autowired IdentityService identity;
 @Autowired UserRepository users;
 @Autowired EmployeeRepository employees;
 @MockitoBean JwtDecoder decoder;
 @MockitoBean(name="notificationClock") Clock clock;
 @Autowired org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt> tokenValidator;
 @BeforeEach void clean() {
  sql.execute("TRUNCATE project_assignments, employees, projects, app_users CASCADE");
  at("2026-10-20T15:55:00Z");
 }
 void at(String instant) {
  var fixed=Clock.fixed(Instant.parse(instant),ZoneOffset.UTC);
  when(clock.instant()).thenReturn(fixed.instant());
  when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  org.mockito.Mockito.doAnswer(a->fixed.withZone(a.getArgument(0))).when(clock).withZone(org.mockito.ArgumentMatchers.any());
 }
 Jwt token(String subject) {
  return Jwt.withTokenValue("test-only").header("alg","RS256").claim("email_verified",true).subject(subject).claim("email",subject+"@example.test").claim("name","Test "+subject).build();
 }
 UUID user(String subject,String role) {
  var user=identity.current(token(subject));
  member(sql,user.getId(),role);
  return user.getId();
 }
 String projectBody(String code) throws Exception {
  return mapper.writeValueAsString(Map.of("code",code,"name","Project "+code,"active",true));
 }
 UUID project(String code) throws Exception {
  var result=mvc.perform(post("/api/v1/projects").with(jwt().jwt(j->j.claim("email_verified",true).subject("admin"))).contentType("application/json").content(projectBody(code)))
   .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn();
  return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
 }
 UUID employee(String subject,String number) { return employees.saveAndFlush(new Employee(ORGANIZATION,user(subject,"EMPLOYEE"),number,null)).getId(); }
 String assignment(UUID employee,UUID project,LocalDate from,LocalDate to,boolean active) throws Exception {
  Map<String,Object> body=new HashMap<>(); body.put("employeeId",employee); body.put("projectId",project);
  body.put("validFrom",from); body.put("validTo",to); body.put("active",active); return mapper.writeValueAsString(body);
 }
 @Test void firstLoginProvisionsEmployeeRoleIgnoringClaims() throws Exception {
  mvc.perform(get("/api/v1/auth/me").with(r->{r.removeHeader("X-Organization-Id");return r;}).with(jwt().jwt(j->j.claim("email_verified",true).subject("alice").claim("email","alice@example.test").claim("name","Alice").claim("role","ADMIN"))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.role").doesNotExist()).andExpect(jsonPath("$.displayName").value("Alice"));
  assertThat(users.count()).isEqualTo(1);
 }
 @Test void existingUserRetainsIdAndDatabaseRole() throws Exception {
  UUID id=user("alice","FINANCE");
  mvc.perform(get("/api/v1/auth/me").with(jwt().jwt(j->j.claim("email_verified",true).subject("alice"))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString())).andExpect(jsonPath("$.role").value("FINANCE"));
  assertThat(users.count()).isEqualTo(1);
 }
 @Test void provisioningIsConcurrentSafe() throws Exception {
  try(var executor=Executors.newFixedThreadPool(4)) {
   var tasks=new ArrayList<Callable<UUID>>();
   for(int i=0;i<8;i++) tasks.add(()->identity.current(token("same-subject")).getId());
   var ids=new HashSet<UUID>();
   for(var result:executor.invokeAll(tasks)) ids.add(result.get());
   assertThat(ids).hasSize(1); assertThat(users.count()).isEqualTo(1);
  }
 }
 @Test void firstAuthenticatedProjectReadAlsoProvisions() throws Exception {
  mvc.perform(get("/api/v1/projects").with(jwt().jwt(j->j.claim("email_verified",true).subject("reader")))).andExpect(status().isForbidden());
  assertThat(users.findByExternalSubject("reader")).isPresent();
 }
 @Test void anonymousIsRejectedWithConsistentError() throws Exception {
  mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized())
   .andExpect(jsonPath("$.code").value("UNAUTHENTICATED")).andExpect(jsonPath("$.path").value("/api/v1/projects"));
 }
 @Test void invalidBearerTokenIsRejected() throws Exception {
  when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid"));
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
 }
 @ParameterizedTest @ValueSource(strings={"EMPLOYEE","MANAGER","FINANCE"})
 void nonAdminsCannotWrite(String role) throws Exception {
  user("reader",role);
  mvc.perform(post("/api/v1/projects").with(jwt().jwt(j->j.claim("email_verified",true).subject("reader").claim("role","ADMIN")))
   .contentType("application/json").content(projectBody("DENIED"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
  mvc.perform(put("/api/v1/projects/"+UUID.randomUUID()).with(jwt().jwt(j->j.claim("email_verified",true).subject("reader")))
   .contentType("application/json").content(projectBody("DENIED"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/project-assignments").with(jwt().jwt(j->j.claim("email_verified",true).subject("reader")))
   .contentType("application/json").content(assignment(UUID.randomUUID(),UUID.randomUUID(),LocalDate.now(),null,true))).andExpect(status().isForbidden());
 }
 @Test void adminCreatesUpdatesAndReadsProject() throws Exception {
  user("user","EMPLOYEE");
  user("admin","ADMIN"); UUID id=project("P-01");
  mvc.perform(put("/api/v1/projects/"+id).with(jwt().jwt(j->j.claim("email_verified",true).subject("admin"))).contentType("application/json").content(projectBody("P-02")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("P-02"));
  mvc.perform(get("/api/v1/projects/"+id).with(jwt().jwt(j->j.claim("email_verified",true)))).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Project P-02"));
 }
 @Test void duplicateProjectCodeAndUpdateConflict() throws Exception {
  user("admin","ADMIN"); project("SAME"); UUID other=project("OTHER");
  mvc.perform(post("/api/v1/projects").with(jwt().jwt(j->j.claim("email_verified",true).subject("admin"))).contentType("application/json").content(projectBody("SAME")))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_PROJECT_CODE"));
  mvc.perform(put("/api/v1/projects/"+other).with(jwt().jwt(j->j.claim("email_verified",true).subject("admin"))).contentType("application/json").content(projectBody("SAME"))).andExpect(status().isConflict());
 }
 @Test void invalidProjectAndPaginationAreRejected() throws Exception {
  user("user","EMPLOYEE");
  user("admin","ADMIN");
  mvc.perform(post("/api/v1/projects").with(jwt().jwt(j->j.claim("email_verified",true).subject("admin"))).contentType("application/json").content(projectBody(" ")))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  mvc.perform(get("/api/v1/projects?size=1000").with(jwt().jwt(j->j.claim("email_verified",true)))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/projects?page=-1").with(jwt().jwt(j->j.claim("email_verified",true)))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/projects/not-a-uuid").with(jwt().jwt(j->j.claim("email_verified",true)))).andExpect(status().isBadRequest());
 }
 @Test void missingResourceIs404() throws Exception {
  user("user","EMPLOYEE");
  mvc.perform(get("/api/v1/projects/"+UUID.randomUUID()).with(jwt().jwt(j->j.claim("email_verified",true)))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
 }
 @Test void createsAssignmentAndPreventsDuplicateActiveAssignment() throws Exception {
  user("admin","ADMIN"); UUID p=project("ASSIGN"); UUID e=employee("alice","E001");
  String body=assignment(e,p,LocalDate.now(),null,true);
  mvc.perform(post("/api/v1/project-assignments").with(jwt().jwt(j->j.claim("email_verified",true).subject("admin"))).contentType("application/json").content(body))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.employeeId").value(e.toString()));
  mvc.perform(post("/api/v1/project-assignments").with(jwt().jwt(j->j.claim("email_verified",true).subject("admin"))).contentType("application/json").content(body))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_ASSIGNMENT"));
  mvc.perform(post("/api/v1/project-assignments").with(jwt().jwt(j->j.claim("email_verified",true).subject("admin"))).contentType("application/json").content(assignment(e,p,LocalDate.now(),null,false)))
   .andExpect(status().isCreated());
 }
 @Test void invalidDatesAndMissingEmployeeAreRejected() throws Exception {
  user("admin","ADMIN"); UUID p=project("DATES"); UUID e=employee("alice","E001");
  mvc.perform(post("/api/v1/project-assignments").with(jwt().jwt(j->j.claim("email_verified",true).subject("admin"))).contentType("application/json").content(assignment(e,p,LocalDate.now(),LocalDate.now().minusDays(1),true)))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ASSIGNMENT_DATES"));
  mvc.perform(post("/api/v1/project-assignments").with(jwt().jwt(j->j.claim("email_verified",true).subject("admin"))).contentType("application/json").content(assignment(UUID.randomUUID(),p,LocalDate.now(),null,true)))
   .andExpect(status().isNotFound());
 }
 @ParameterizedTest @CsvSource({"2026-10-20T15:55:00Z,2026-10-20", "2026-10-20T23:11:48Z,2026-10-21"})
 void assignedProjectsRespectOwnerDatesAndActiveFlags(String instant,String businessDate) throws Exception {
  at(instant);
  user("admin","ADMIN"); UUID alice=employee("alice","E001"); UUID bob=employee("bob","E002");
  // Fixture dates follow the explicit Athens calendar, even when UTC is still yesterday.
  LocalDate today=LocalDate.parse(businessDate);
  for(int i=0;i<6;i++) {
   UUID p=project("P"+i);
   mvc.perform(post("/api/v1/project-assignments").with(jwt().jwt(j->j.claim("email_verified",true).subject("admin"))).contentType("application/json")
    .content(assignment(i==1?bob:alice,p,i==2?today.plusDays(1):today.minusDays(5),i==3?today.minusDays(1):null,i!=4))).andExpect(status().isCreated());
   if(i==5) sql.update("UPDATE projects SET active=false WHERE id=?",p);
  }
  mvc.perform(get("/api/v1/employees/me/projects").with(jwt().jwt(j->j.claim("email_verified",true).subject("alice"))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].code").value("P0"));
  sql.update("UPDATE employees SET active=false WHERE id=?",alice);
  mvc.perform(get("/api/v1/employees/me/projects").with(jwt().jwt(j->j.claim("email_verified",true).subject("alice")))).andExpect(jsonPath("$.totalElements").value(0));
 }
 @Test void noEmployeeReturnsEmptyPage() throws Exception {
  user("user","EMPLOYEE");
  mvc.perform(get("/api/v1/employees/me/projects").with(jwt().jwt(j->j.claim("email_verified",true)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
 }
 @Test void databaseEnforcesEmployeeUniquenessAndForeignKeys() {
  employee("alice","E001");
  UUID bob=user("bob","EMPLOYEE");
  assertThatThrownBy(()->employees.saveAndFlush(new Employee(ORGANIZATION,bob,"E001",null))).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->employees.saveAndFlush(new Employee(ORGANIZATION,UUID.randomUUID(),"E003",null))).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->employees.saveAndFlush(new Employee(ORGANIZATION,bob,"E004",UUID.randomUUID()))).isInstanceOf(DataIntegrityViolationException.class);
 }
 @Test void databaseEnforcesDatesAndAssignmentForeignKeys() throws Exception {
  user("admin","ADMIN"); UUID p=project("DB"); UUID e=employee("alice","E001");
  assertThatThrownBy(()->sql.update("INSERT INTO project_assignments(id,employee_id,project_id,valid_from,valid_to) VALUES (?,?,?,CURRENT_DATE,CURRENT_DATE-1)",UUID.randomUUID(),e,p)).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->sql.update("INSERT INTO project_assignments(id,employee_id,project_id,valid_from) VALUES (?,?,?,CURRENT_DATE)",UUID.randomUUID(),UUID.randomUUID(),p)).isInstanceOf(DataIntegrityViolationException.class);
 }
 @Test void validatesIssuerAudienceExpiryAndSubject() {
  Instant now=Instant.now();
  var valid=Jwt.withTokenValue("test").header("alg","RS256").issuer("http://localhost:8180/realms/workforce")
   .claim("email_verified",true).subject("valid-subject").audience(List.of("workforce-api")).issuedAt(now).expiresAt(now.plusSeconds(300)).build();
  assertThat(tokenValidator.validate(valid).hasErrors()).isFalse();
  assertThat(tokenValidator.validate(Jwt.withTokenValue("test").header("alg","RS256").claims(c->{c.putAll(valid.getClaims()); c.remove("sub");}).build()).hasErrors()).isTrue();
  assertThat(tokenValidator.validate(Jwt.withTokenValue("test").header("alg","RS256").claims(c->{c.putAll(valid.getClaims()); c.put("aud",List.of("other"));}).build()).hasErrors()).isTrue();
  assertThat(tokenValidator.validate(Jwt.withTokenValue("test").header("alg","RS256").claims(c->{c.putAll(valid.getClaims()); c.put("iss","https://untrusted.example");}).build()).hasErrors()).isTrue();
  assertThat(tokenValidator.validate(Jwt.withTokenValue("test").header("alg","RS256").claims(c->{c.putAll(valid.getClaims()); c.put("iat",now.minusSeconds(600)); c.put("exp",now.minusSeconds(120));}).build()).hasErrors()).isTrue();
 }
 @Test void healthAndOpenApiAreAvailable() throws Exception {
  mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/v1/auth/me']").exists());
 }
}
