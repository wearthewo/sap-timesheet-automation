package com.acme.workforce.organization.application;

import com.acme.workforce.common.application.DomainException;
import com.acme.workforce.identity.domain.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Request-only access. Workers carry persisted organization IDs, never this request proxy. */
@Component
public class OrganizationAccess {
 private final HttpServletRequest request;
 private final JdbcTemplate sql;
 public OrganizationAccess(HttpServletRequest request, JdbcTemplate sql) { this.request=request; this.sql=sql; }
 public UUID id() {
  String value=request.getHeader("X-Organization-Id");
  try { return UUID.fromString(value); }
  catch (IllegalArgumentException | NullPointerException e) {
   throw new DomainException(400,"ORGANIZATION_REQUIRED","Select an organization before continuing.");
  }
 }
 public UserRole role(UUID userId) {
  return sql.query("""
   SELECT m.role FROM organization_memberships m JOIN organizations o ON o.id=m.organization_id
   WHERE m.user_id=? AND m.organization_id=? AND m.status='ACTIVE' AND o.status='ACTIVE'
   """,(r,n)->UserRole.valueOf(r.getString(1)),userId,id()).stream().findFirst()
   .orElseThrow(()->new DomainException(403,"ORGANIZATION_ACCESS_DENIED","Active organization membership is required."));
 }
 public record Membership(UUID id,String slug,String name,UserRole role) {}
 public List<Membership> memberships(UUID userId) {
  return sql.query("""
   SELECT o.id,o.slug,o.name,m.role FROM organization_memberships m JOIN organizations o ON o.id=m.organization_id
   WHERE m.user_id=? AND m.status='ACTIVE' AND o.status='ACTIVE' ORDER BY o.name,o.id
   """,(r,n)->new Membership(r.getObject(1,UUID.class),r.getString(2),r.getString(3),UserRole.valueOf(r.getString(4))),userId);
 }
}
