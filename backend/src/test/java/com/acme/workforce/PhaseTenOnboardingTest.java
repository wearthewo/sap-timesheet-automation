package com.acme.workforce;

import com.acme.workforce.identity.application.IdentityService;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PhaseTenOnboardingTest {
 @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p) {p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);}
 @Autowired MockMvc mvc; @Autowired JdbcTemplate sql; @Autowired IdentityService identity; @Autowired ObjectMapper json;
 @MockitoBean JwtDecoder decoder;
 @BeforeEach void reset() {sql.execute("TRUNCATE organizations,app_users CASCADE");}
 Jwt token(String sub) {return Jwt.withTokenValue("test").header("alg","RS256").subject(sub).claim("email","shared@example.test").claim("email_verified",true).build();}
 ResultActions create(String who,UUID key,String slug)throws Exception{return mvc.perform(post("/api/v1/organizations").with(jwt().jwt(token(who))).header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsString(Map.of("name","Example & Team","slug",slug))));}
 UUID created(String who,UUID key,String slug)throws Exception{return UUID.fromString(json.readTree(create(who,key,slug).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText());}
 int count(String table){return sql.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
 @Test void anonymousCannotCreate()throws Exception {mvc.perform(post("/api/v1/organizations")).andExpect(status().isUnauthorized());assertThat(count("organizations")).isZero();}
 @Test void absentFalseAndStringVerificationClaimsCannotCreateOrAccessExistingMembership()throws Exception {
  UUID org=created("owner",UUID.randomUUID(),"existing");
  for(Object verified:new Object[]{false,"true","false"}) {
   mvc.perform(post("/api/v1/organizations").with(jwt().jwt(j->j.subject("owner").claim("email_verified",verified))).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{\"name\":\"Other\",\"slug\":\"other\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));
   mvc.perform(get("/api/v1/projects").header("X-Organization-Id",org).with(jwt().jwt(j->j.subject("owner").claim("email_verified",verified)))).andExpect(status().isForbidden());
  }
  mvc.perform(post("/api/v1/organizations").with(jwt().jwt(j->j.subject("owner")))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/auth/me").with(jwt().jwt(j->j.subject("owner")))).andExpect(jsonPath("$.emailVerified").value(false)).andExpect(jsonPath("$.role").doesNotExist());
  mvc.perform(get("/api/v1/auth/organizations").with(jwt().jwt(j->j.subject("owner")))).andExpect(content().json("[]"));
  assertThat(count("organizations")).isEqualTo(1);
 }
 @Test void verifiedOwnerCreatesEmptyOrganizationWithNormalizedSlugAndOnlyLocalAdmin()throws Exception {
  UUID org=created("new",UUID.randomUUID(),"  My-Team  ");
  assertThat(sql.queryForObject("SELECT slug FROM organizations WHERE id=?",String.class,org)).isEqualTo("my-team");
  assertThat(count("organization_memberships")).isEqualTo(1);
  assertThat(sql.queryForObject("SELECT role FROM organization_memberships",String.class)).isEqualTo("ADMIN");
  assertThat(sql.queryForObject("SELECT role FROM app_users",String.class)).isEqualTo("EMPLOYEE");
  for(String table:List.of("employees","projects","timesheets","notifications","sap_timesheet_exports"))assertThat(count(table)).isZero();
  mvc.perform(get("/api/v1/auth/me").header("X-Organization-Id",org).with(jwt().jwt(token("new")))).andExpect(jsonPath("$.role").value("ADMIN")).andExpect(jsonPath("$.hasEmployeeProfile").value(false));
 }
 @ParameterizedTest @ValueSource(strings={"ownerId","userId","role","status","organizationId","membershipStatus"})
 void untrustedFieldsRejected(String field)throws Exception {
  mvc.perform(post("/api/v1/organizations").with(jwt().jwt(token("new"))).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(json.writeValueAsString(Map.of("name","Test","slug","valid-slug",field,"ADMIN"))))
   .andExpect(status().isBadRequest());assertThat(count("organizations")).isZero();assertThat(count("organization_memberships")).isZero();
 }
 @ParameterizedTest @ValueSource(strings={"admin","legacy","api","ab","-team","team-","a--b","has space","has/slash","Τeam"})
 void invalidAndReservedSlugsRejected(String slug)throws Exception {create("new",UUID.randomUUID(),slug).andExpect(status().isBadRequest());assertThat(count("organizations")).isZero();}
 @Test void invalidNameAndMissingKeyAreRejected()throws Exception {
  mvc.perform(post("/api/v1/organizations").with(jwt().jwt(token("new"))).contentType("application/json").content("{\"name\":\"Test\",\"slug\":\"valid\"}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/organizations").with(jwt().jwt(token("new"))).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{\"name\":\"  \",\"slug\":\"valid\"}")).andExpect(status().isBadRequest());
 }
 @Test void repeatedRequestReturnsSameOrganizationButChangedBodyConflicts()throws Exception {
  UUID key=UUID.randomUUID(),id=created("same",key,"original");
  assertThat(created("same",key,"original")).isEqualTo(id);
  create("same",key,"different").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
  assertThat(count("organizations")).isEqualTo(1);assertThat(count("organization_creation_requests")).isEqualTo(1);
 }
 @Test void duplicateSlugNeverGrantsExistingOrganizationMembership()throws Exception {
  created("first",UUID.randomUUID(),"taken");
  create("other",UUID.randomUUID(),"TAKEN").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORGANIZATION_SLUG_TAKEN"));
  assertThat(count("organization_memberships")).isEqualTo(1);
 }
 @Test void normalizedIndexAlsoProtectsPreviouslyOperatorCreatedSlugs()throws Exception {
  sql.update("INSERT INTO organizations(id,slug,name,status) VALUES (?,' Older-Slug ','Older','ACTIVE')",UUID.randomUUID());
  create("new",UUID.randomUUID(),"older-slug").andExpect(status().isConflict());assertThat(count("organization_memberships")).isZero();
 }
 @Test void concurrentRepeatedSubmissionCreatesOnlyOneOrganization()throws Exception {
  UUID key=UUID.randomUUID();var start=new CyclicBarrier(2);
  try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
   Callable<UUID> task=()->{start.await();return created("same",key,"concurrent");};var a=pool.submit(task);var b=pool.submit(task);
   assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
  }
  assertThat(count("app_users")).isEqualTo(1);assertThat(count("organizations")).isEqualTo(1);assertThat(count("organization_memberships")).isEqualTo(1);
 }
 @Test void concurrentSlugCollisionHasOneWinner()throws Exception {
  var start=new CyclicBarrier(2);
  try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
   var a=pool.submit(()->{start.await();return create("one",UUID.randomUUID(),"collision").andReturn().getResponse().getStatus();});
   var b=pool.submit(()->{start.await();return create("two",UUID.randomUUID(),"COLLISION").andReturn().getResponse().getStatus();});
   assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
  }
  assertThat(count("organizations")).isEqualTo(1);assertThat(count("organization_memberships")).isEqualTo(1);
 }
 @Test void failedMembershipInsertRollsBackOrganization()throws Exception {
  sql.execute("CREATE FUNCTION reject_test_membership() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rejection' USING ERRCODE='23514'; END $$; CREATE TRIGGER reject_test_membership BEFORE INSERT ON organization_memberships FOR EACH ROW EXECUTE FUNCTION reject_test_membership()");
  try {create("new",UUID.randomUUID(),"rollback").andExpect(status().isConflict());assertThat(count("organizations")).isZero();assertThat(count("organization_creation_requests")).isZero();}
  finally {sql.execute("DROP TRIGGER reject_test_membership ON organization_memberships; DROP FUNCTION reject_test_membership()");}
 }
 @Test void suspendedMembershipAndOrganizationAreNotReactivatedByReplay()throws Exception {
  UUID key=UUID.randomUUID(),id=created("owner",key,"suspended");
  // Phase 11 retains an active administrator while testing suspension/replay of the creator.
  UUID owner=identity.current(token("owner")).getId(),backup=identity.current(token("backup-admin")).getId();
  sql.update("INSERT INTO organization_memberships(id,organization_id,user_id,role,status) VALUES (?,?,?,'ADMIN','ACTIVE')",UUID.randomUUID(),id,backup);
  sql.update("UPDATE organization_memberships SET status='SUSPENDED' WHERE organization_id=? AND user_id=?",id,owner);
  create("owner",key,"suspended").andExpect(status().isForbidden());
  assertThat(sql.queryForObject("SELECT status FROM organization_memberships WHERE organization_id=? AND user_id=?",String.class,id,owner)).isEqualTo("SUSPENDED");
  sql.update("UPDATE organization_memberships SET status='ACTIVE';UPDATE organizations SET status='SUSPENDED'");
  create("owner",key,"suspended").andExpect(status().isForbidden());
 }
 @Test void provisioningIsIdempotentAndSharedEmailDoesNotMergeSubjects() {
  var a=identity.current(token("a"));var again=identity.current(token("a"));var b=identity.current(token("b"));
  assertThat(a.getId()).isEqualTo(again.getId()).isNotEqualTo(b.getId());assertThat(a.getDisplayName()).isEqualTo("Employee");assertThat(count("organization_memberships")).isZero();
 }
 @Test void newOrganizationCannotAccessOtherOrganizationsData()throws Exception {
  UUID first=created("one",UUID.randomUUID(),"first"),second=created("two",UUID.randomUUID(),"second");
  UUID project=UUID.randomUUID();sql.update("INSERT INTO projects(id,organization_id,code,name) VALUES (?,?,'PRIVATE','Private')",project,first);
  mvc.perform(get("/api/v1/projects/"+project).header("X-Organization-Id",second).with(jwt().jwt(token("two")))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/projects").header("X-Organization-Id",first).with(jwt().jwt(token("two")))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/projects").header("X-Organization-Id",first).with(jwt().jwt(token("no-member")))).andExpect(status().isForbidden());
 }
}
