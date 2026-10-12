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
 public IdentityController(IdentityService identity, com.acme.workforce.organization.application.OrganizationAccess organization) { this.organization=organization; this.identity=identity; }
 public record Me(UUID id,String email,String displayName,UserRole role,UUID organizationId,String organizationName) {}
 @GetMapping("/organizations")
 public java.util.List<com.acme.workforce.organization.application.OrganizationAccess.Membership> memberships(@AuthenticationPrincipal Jwt jwt) { return organization.memberships(identity.current(jwt).getId()); }
 @GetMapping("/me")
 public Me me(@AuthenticationPrincipal Jwt jwt, jakarta.servlet.http.HttpServletRequest request) {
  var user=identity.current(jwt);
  var membership=request.getHeader("X-Organization-Id")==null ? null : organization.memberships(user.getId()).stream().filter(m->m.id().equals(organization.id())).findFirst().orElseThrow(()->new com.acme.workforce.common.application.DomainException(403,"ORGANIZATION_ACCESS_DENIED","Active organization membership is required."));
  return new Me(user.getId(),user.getEmail(),user.getDisplayName(),membership==null?null:membership.role(),membership==null?null:membership.id(),membership==null?null:membership.name());
 }
}
