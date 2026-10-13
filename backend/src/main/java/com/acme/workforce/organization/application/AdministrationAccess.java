package com.acme.workforce.organization.application;

import com.acme.workforce.common.application.DomainException;
import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.identity.domain.UserRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.UUID;

@Component
public class AdministrationAccess {
 private final JdbcTemplate sql; private final OrganizationAccess organization; private final IdentityService identity;
 public AdministrationAccess(JdbcTemplate sql,OrganizationAccess organization,IdentityService identity){this.sql=sql;this.organization=organization;this.identity=identity;}
 public UUID actor(){return identity.current(((JwtAuthenticationToken)SecurityContextHolder.getContext().getAuthentication()).getToken()).getId();}
 public UUID read(){if(organization.role(actor())!=UserRole.ADMIN)throw new DomainException(403,"FORBIDDEN","Administrator access is required.");return organization.id();}
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID write(){UUID org=organization.id();sql.queryForObject("SELECT id FROM organizations WHERE id=? FOR UPDATE",UUID.class,org);return read();}
 @Transactional(propagation=Propagation.MANDATORY)
 public void audit(UUID org,String action,UUID resource,String details){sql.update("INSERT INTO organization_admin_audit(id,organization_id,actor_user_id,action,resource_id,details) VALUES (?,?,?,?,?,?::jsonb)",UUID.randomUUID(),org,actor(),action,resource,details);}
 public static void version(long actual,long expected){if(actual!=expected)throw new DomainException(409,"CONCURRENT_MODIFICATION","This record changed. Reload it before continuing.");}
}
