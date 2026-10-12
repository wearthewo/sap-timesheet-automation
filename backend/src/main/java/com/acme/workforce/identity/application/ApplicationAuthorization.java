package com.acme.workforce.identity.application;

import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.acme.workforce.identity.domain.UserRole;
@Component
public class ApplicationAuthorization {
 private final com.acme.workforce.organization.application.OrganizationAccess organization;
 private final IdentityService identity;
 public ApplicationAuthorization(IdentityService identity, com.acme.workforce.organization.application.OrganizationAccess organization) { this.organization=organization; this.identity=identity; }
 public boolean isAdmin(Authentication authentication) {
  return authentication instanceof JwtAuthenticationToken token && organization.role(identity.current(token.getToken()).getId())==UserRole.ADMIN;
 }
}
