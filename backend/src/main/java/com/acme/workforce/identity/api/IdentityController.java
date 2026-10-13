package com.acme.workforce.identity.api;

import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.identity.domain.*;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/auth")
public class IdentityController {
 private final com.acme.workforce.organization.application.OrganizationAccess organization;
 private final IdentityService identity;
 private final com.acme.workforce.employee.infrastructure.EmployeeRepository employees;
 public IdentityController(IdentityService identity, com.acme.workforce.organization.application.OrganizationAccess organization,com.acme.workforce.employee.infrastructure.EmployeeRepository employees) { this.organization=organization; this.identity=identity; this.employees=employees; }
 public record Me(UUID id,String email,String displayName,UserRole role,UUID organizationId,String organizationName,boolean emailVerified,boolean hasEmployeeProfile) {}
 @GetMapping("/organizations")
 public java.util.List<com.acme.workforce.organization.application.OrganizationAccess.Membership> memberships(@AuthenticationPrincipal Jwt jwt) {
  var user=identity.current(jwt);
  return com.acme.workforce.identity.application.VerifiedIdentity.emailVerified(jwt)?organization.memberships(user.getId()):java.util.List.of();
 }
 @GetMapping("/me")
 public Me me(@AuthenticationPrincipal Jwt jwt, jakarta.servlet.http.HttpServletRequest request) {
  var user=identity.current(jwt);
  var membership=request.getHeader("X-Organization-Id")==null ? null : organization.memberships(user.getId()).stream().filter(m->m.id().equals(organization.id())).findFirst().orElseThrow(()->new com.acme.workforce.common.application.DomainException(403,"ORGANIZATION_ACCESS_DENIED","Active organization membership is required."));
  boolean hasEmployee=membership!=null && employees.findByUserIdAndOrganizationId(user.getId(),membership.id()).filter(e->e.isActive()).isPresent();
  return new Me(user.getId(),user.getEmail(),user.getDisplayName(),membership==null?null:membership.role(),membership==null?null:membership.id(),membership==null?null:membership.name(),com.acme.workforce.identity.application.VerifiedIdentity.emailVerified(jwt),hasEmployee);
 }
}
