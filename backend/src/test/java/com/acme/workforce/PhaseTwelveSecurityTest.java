package com.acme.workforce;

import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.employee.domain.Employee;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.security.sensitive-requests-per-minute=2") @AutoConfigureMockMvc @Testcontainers
class PhaseTwelveSecurityTest extends TenantFixtures {
 @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);}
 @Autowired MockMvc mvc; @Autowired JdbcTemplate sql; @Autowired IdentityService identity; @Autowired EmployeeRepository employees;
 @Autowired OAuth2TokenValidator<Jwt> validator;
 @MockitoBean JwtDecoder decoder;
 @MockitoBean(name="notificationClock") Clock clock;
 @BeforeEach void clean(){sql.execute("TRUNCATE app_users,employees,projects CASCADE");}
 @ParameterizedTest @CsvSource({
  "2026-01-31T22:30:00Z,2026-02-01", "2026-02-28T22:30:00Z,2026-03-01",
  "2028-02-29T22:30:00Z,2028-03-01", "2026-04-30T21:30:00Z,2026-05-01",
  "2026-03-28T22:30:00Z,2026-03-29", "2026-03-29T21:30:00Z,2026-03-30",
  "2026-10-24T21:30:00Z,2026-10-25", "2026-10-25T22:30:00Z,2026-10-26"})
 void assignmentValidityUsesAthensCalendarAndPreservesDateRoundTrip(String instant,String expected)throws Exception{
  var fixed=Clock.fixed(Instant.parse(instant),ZoneOffset.UTC);
  when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  when(clock.withZone(any())).thenAnswer(a->fixed.withZone(a.getArgument(0)));
  var token=Jwt.withTokenValue("test").header("alg","RS256").subject("calendar").claim("email","calendar@example.test").claim("email_verified",true).build();
  var user=identity.current(token);member(sql,user.getId(),"ADMIN");
  var employee=employees.saveAndFlush(new Employee(ORGANIZATION,user.getId(),"DATE-01",null));
  var today=LocalDate.parse(expected);var project=UUID.randomUUID();var expired=UUID.randomUUID();
  for(var id:List.of(project,expired))sql.update("INSERT INTO projects(id,organization_id,code,name,active,created_at,updated_at) VALUES (?,?,?,? ,true,now(),now())",id,ORGANIZATION,id.toString(),"Calendar project");
  sql.update("INSERT INTO project_assignments(id,organization_id,employee_id,project_id,valid_from,valid_to,active,created_at,updated_at) VALUES (?,?,?,?,?,?,true,now(),now())",UUID.randomUUID(),ORGANIZATION,employee.getId(),project,today,today);
  sql.update("INSERT INTO project_assignments(id,organization_id,employee_id,project_id,valid_from,valid_to,active,created_at,updated_at) VALUES (?,?,?,?,?,?,true,now(),now())",UUID.randomUUID(),ORGANIZATION,employee.getId(),expired,today.minusDays(1),today.minusDays(1));
  mvc.perform(get("/api/v1/employees/me/projects").with(jwt().jwt(token)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(project.toString()));
  mvc.perform(get("/api/v1/admin/projects/"+project+"/assignments").with(jwt().jwt(token)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].validFrom").value(expected)).andExpect(jsonPath("$.content[0].validTo").value(expected));
  assertThat(sql.queryForObject("SELECT valid_from FROM project_assignments WHERE project_id=?",LocalDate.class,project)).isEqualTo(today);
 }
 @Test void jwtWithoutExpirationCannotBecomeAnUnboundedSession(){
  var token=Jwt.withTokenValue("test").header("alg","RS256").subject("no-expiration").issuer("http://localhost:8180/realms/workforce").audience(List.of("workforce-api")).build();
  assertThat(validator.validate(token).hasErrors()).isTrue();
 }

 @Test void oversizedAuthenticatedApiBodyHasStructuredErrorAndNoWrite()throws Exception{
  var token=Jwt.withTokenValue("test").header("alg","RS256").subject("body-limit").claim("email","body@example.test").claim("email_verified",true).build();
  mvc.perform(post("/api/v1/projects").with(jwt().jwt(token)).contentType("application/json").content("x".repeat(65537)))
   .andExpect(status().is(413)).andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
  assertThat(sql.queryForObject("SELECT count(*) FROM app_users",Integer.class)).isZero();
 }
 @Test void sensitiveEndpointReturnsBoundedStructuredRateLimit()throws Exception{
  var token=Jwt.withTokenValue("test").header("alg","RS256").subject("limited").claim("email","limited@example.test").claim("email_verified",true).build();
  for(int i=0;i<2;i++)mvc.perform(post("/api/v1/invitations/preview").with(jwt().jwt(token)).contentType("application/json").content("{}"))
    .andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/invitations/preview").with(jwt().jwt(token)).contentType("application/json").content("{}"))
    .andExpect(status().is(429)).andExpect(header().string("Retry-After","60")).andExpect(jsonPath("$.code").value("RATE_LIMITED"));
 }
 @Test void anonymousOversizedMutationStillRequiresAuthentication()throws Exception{
  mvc.perform(post("/api/v1/projects").contentType("application/json").content("x".repeat(65537))).andExpect(status().isUnauthorized());
 }
}
