package com.acme.workforce;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Import(TenantFixtures.Config.class)
abstract class TenantFixtures {
 @org.springframework.beans.factory.annotation.Autowired com.acme.workforce.organization.application.OrganizationAccess organizationAccess;
 static final UUID ORGANIZATION=UUID.fromString("00000000-0000-0000-0000-000000000001");
 @BeforeEach void bindOrganizationRequest() {
  var request=new MockHttpServletRequest();request.addHeader("X-Organization-Id",ORGANIZATION);
  RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
 }
 @AfterEach void clearOrganizationRequest() { RequestContextHolder.resetRequestAttributes(); }
 static UUID member(JdbcTemplate sql,UUID user,String role) {
  sql.update("INSERT INTO organization_memberships(id,organization_id,user_id,role,status) VALUES (?,?,?,?,'ACTIVE') ON CONFLICT(organization_id,user_id) DO UPDATE SET role=excluded.role,status='ACTIVE'",UUID.randomUUID(),ORGANIZATION,user,role);
  return user;
 }
 @TestConfiguration static class Config {
  @Bean MockMvcBuilderCustomizer organizationHeader() { return builder->builder.defaultRequest(get("/").header("X-Organization-Id",ORGANIZATION)); }
 }
}
