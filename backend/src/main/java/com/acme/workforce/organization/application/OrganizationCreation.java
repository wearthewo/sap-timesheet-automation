package com.acme.workforce.organization.application;

import com.acme.workforce.common.application.DomainException;
import com.acme.workforce.identity.application.VerifiedIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class OrganizationCreation {
 private static final Set<String> RESERVED=Set.of("admin","api","app","auth","billing","help","keycloak","legacy","login","logout","mail","onboarding","organizations","register","root","security","support","system","workforce","www");
 private final JdbcTemplate sql;
 public OrganizationCreation(JdbcTemplate sql) { this.sql=sql; }
 public record Created(UUID id,String slug,String name,String role) {}
 @Transactional
 public Created create(UUID userId,Jwt jwt,UUID key,String requestedName,String requestedSlug) {
  VerifiedIdentity.requireVerifiedEmail(jwt);
  String name=requestedName.strip(),slug=requestedSlug.strip().toLowerCase(Locale.ROOT);
  if(name.isBlank() || name.length()>200 || name.codePoints().anyMatch(Character::isISOControl))
   throw new DomainException(400,"INVALID_ORGANIZATION_NAME","Enter an organization name of 1–200 characters without control characters.");
  if(slug.length()<3 || slug.length()>63 || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || RESERVED.contains(slug))
   throw new DomainException(400,"INVALID_ORGANIZATION_SLUG","Use 3–63 lowercase letters, numbers or single hyphens. This address must not be reserved.");
  // Serialize this identity's creation requests, including simultaneous submissions from two tabs.
  sql.queryForObject("SELECT id FROM app_users WHERE id=? FOR UPDATE",UUID.class,userId);
  var replay=sql.query("SELECT organization_id,requested_name,requested_slug FROM organization_creation_requests WHERE user_id=? AND idempotency_key=?",
   (r,n)->new Created(r.getObject(1,UUID.class),r.getString(3),r.getString(2),"ADMIN"),userId,key);
  if(!replay.isEmpty()) {
   var previous=replay.getFirst();
   if(!previous.name().equals(name)||!previous.slug().equals(slug)) throw new DomainException(409,"IDEMPOTENCY_CONFLICT","This creation request was already used. Reload the form for a different organization.");
   boolean accessible=Boolean.TRUE.equals(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM organization_memberships m JOIN organizations o ON o.id=m.organization_id WHERE m.user_id=? AND o.id=? AND m.status='ACTIVE' AND o.status='ACTIVE' AND m.role='ADMIN')",Boolean.class,userId,previous.id()));
   if(!accessible) throw new DomainException(403,"ORGANIZATION_ACCESS_DENIED","The original organization is no longer available with an active administrator membership.");
   return previous;
  }
  UUID id=UUID.randomUUID();
  int inserted=sql.update("INSERT INTO organizations(id,slug,name,status) VALUES (?,?,?,'ACTIVE') ON CONFLICT DO NOTHING",id,slug,name);
  if(inserted==0) throw new DomainException(409,"ORGANIZATION_SLUG_TAKEN","That organization address is already in use. Choose another.");
  sql.update("INSERT INTO organization_memberships(id,organization_id,user_id,role,status) VALUES (?,?,?,'ADMIN','ACTIVE')",UUID.randomUUID(),id,userId);
  sql.update("INSERT INTO organization_creation_requests(user_id,idempotency_key,organization_id,requested_name,requested_slug) VALUES (?,?,?,?,?)",userId,key,id,name,slug);
  return new Created(id,slug,name,"ADMIN");
 }
}
