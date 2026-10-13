package com.acme.workforce.organization.application;
import com.acme.workforce.common.application.DomainException;
import com.acme.workforce.identity.application.*;
import com.acme.workforce.identity.domain.UserRole;
import com.acme.workforce.notification.application.NotificationPolicy;
import com.acme.workforce.notification.domain.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.*;
import org.springframework.beans.factory.annotation.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

@Service @Transactional(readOnly=true)
public class Invitations {
 private final JdbcTemplate sql;private final AdministrationAccess access;private final InvitationTokens tokens;private final IdentityService identity;private final Clock clock;private final NotificationPolicy mail;private final Duration ttl;
 public Invitations(JdbcTemplate sql,AdministrationAccess access,InvitationTokens tokens,IdentityService identity,@Qualifier("notificationClock") Clock clock,NotificationPolicy mail,@Value("${app.invitations.validity:PT168H}") Duration ttl){this.sql=sql;this.access=access;this.tokens=tokens;this.identity=identity;this.clock=clock;this.mail=mail;this.ttl=ttl;if(ttl.isNegative()||ttl.isZero()||ttl.compareTo(Duration.ofDays(30))>0)throw new IllegalArgumentException("Invitation validity must be positive and at most 30 days");}
 public record Create(@NotBlank @Email @Size(max=320) String email,@NotNull UserRole role,UUID employeeId){}
 public record Token(@NotBlank @Size(max=200) String token){}
 public record View(UUID id,String email,String role,UUID employeeId,String status,Instant expiresAt,Instant createdAt,String deliveryStatus){}
 public record Preview(UUID organizationId,String organizationName,String role,String status){}
 private final RowMapper<View> mapper=(r,n)->new View(r.getObject("id",UUID.class),r.getString("email"),r.getString("role"),r.getObject("employee_id",UUID.class),effective(r.getString("status"),r.getTimestamp("expires_at").toInstant()),r.getTimestamp("expires_at").toInstant(),r.getTimestamp("created_at").toInstant(),r.getString("delivery_status"));
 private String effective(String status,Instant expiry){return status.equals("PENDING")&&!expiry.isAfter(clock.instant())?"EXPIRED":status;}
 private static final String SELECT="SELECT i.*,(SELECT n.status FROM notifications n WHERE n.invitation_id=i.id LIMIT 1) delivery_status FROM organization_invitations i";
 public Page<View> list(String status,Pageable page){UUID org=access.read();String where=" WHERE i.organization_id=? AND (?='' OR CASE WHEN i.status='PENDING' AND i.expires_at<=? THEN 'EXPIRED' ELSE i.status END=?)";var now=Timestamp.from(clock.instant());long count=sql.queryForObject("SELECT count(*) FROM organization_invitations i"+where,Long.class,org,status,now,status);return new PageImpl<>(sql.query(SELECT+where+" ORDER BY i.created_at DESC,i.id DESC LIMIT ? OFFSET ?",mapper,org,status,now,status,page.getPageSize(),page.getOffset()),page,count);}
 @Transactional public View create(Create r){UUID org=access.write(),id=UUID.randomUUID();String token=tokens.token(id),email=r.email().strip().toLowerCase(Locale.ROOT);Instant now=clock.instant(),expiry=now.plus(ttl);
  if(email.contains("\r")||email.contains("\n"))throw new DomainException(400,"VALIDATION_ERROR","Enter a valid email address.");
  sql.update("UPDATE organization_invitations SET status='EXPIRED',updated_at=? WHERE organization_id=? AND status='PENDING' AND expires_at<=?",Timestamp.from(now),org,Timestamp.from(now));
  if(r.employeeId()!=null){var employees=sql.queryForList("SELECT user_id,active FROM employees WHERE organization_id=? AND id=? FOR UPDATE",org,r.employeeId());if(employees.isEmpty())throw DomainException.notFound("Employee");if(employees.getFirst().get("user_id")!=null)throw new DomainException(409,"EMPLOYEE_ALREADY_LINKED","Choose an employee without an account.");if(!Boolean.TRUE.equals(employees.getFirst().get("active")))throw new DomainException(409,"EMPLOYEE_INACTIVE","Activate the employee before inviting them.");}
  sql.update("INSERT INTO organization_invitations(id,organization_id,employee_id,email,role,token_hash,status,expires_at,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,'PENDING',?,?,?,?)",id,org,r.employeeId(),email,r.role().name(),tokens.hash(token),Timestamp.from(expiry),access.actor(),Timestamp.from(now),Timestamp.from(now));
  sql.update("""
   INSERT INTO notifications(id,organization_id,type,recipient_email,subject,body,status,deduplication_key,next_attempt_at,invitation_id)
   VALUES (?,?,'ORGANIZATION_INVITATION',?,'Workforce organization invitation','An administrator invited you to join their organization.','PENDING',?,?,?)
   """,UUID.randomUUID(),org,email,"ORGANIZATION_INVITATION:"+id,Timestamp.from(now),id);
  access.audit(org,"INVITATION_CREATED",id,"{\"role\":\""+r.role()+"\"}");return sql.queryForObject(SELECT+" WHERE i.id=? AND i.organization_id=?",mapper,id,org);}
 @Transactional public void revoke(UUID id){UUID org=access.write();var rows=sql.queryForList("SELECT status FROM organization_invitations WHERE id=? AND organization_id=? FOR UPDATE",String.class,id,org);if(rows.isEmpty())throw DomainException.notFound("Invitation");if(!rows.getFirst().equals("PENDING"))throw new DomainException(409,"INVITATION_NOT_PENDING","Only pending invitations can be revoked.");sql.update("UPDATE organization_invitations SET status='REVOKED',updated_at=? WHERE id=?",Timestamp.from(clock.instant()),id);access.audit(org,"INVITATION_REVOKED",id,"{}");}
 private Map<String,Object> owned(String token,Jwt jwt){VerifiedIdentity.requireVerifiedEmail(jwt);String email=jwt.getClaimAsString("email");if(email==null||email.isBlank())throw unavailable();var rows=sql.queryForList("SELECT i.*,o.name organization_name,o.status organization_status FROM organization_invitations i JOIN organizations o ON o.id=i.organization_id WHERE i.token_hash=? AND i.email=?",tokens.hash(token),email.strip().toLowerCase(Locale.ROOT));if(rows.isEmpty())throw unavailable();return rows.getFirst();}
 private DomainException unavailable(){return new DomainException(404,"INVITATION_UNAVAILABLE","This invitation is unavailable for the signed-in verified account.");}
 public Preview preview(String token,Jwt jwt){var i=owned(token,jwt);return new Preview((UUID)i.get("organization_id"),(String)i.get("organization_name"),(String)i.get("role"),effective((String)i.get("status"),((Timestamp)i.get("expires_at")).toInstant()));}
 @Transactional public Preview accept(String token,Jwt jwt){var initial=owned(token,jwt);UUID org=(UUID)initial.get("organization_id"),id=(UUID)initial.get("id"),user=identity.current(jwt).getId();
  sql.queryForObject("SELECT id FROM organizations WHERE id=? FOR UPDATE",UUID.class,org);
  var i=owned(token,jwt);if(!i.get("organization_status").equals("ACTIVE"))throw unavailable();
  if(i.get("status").equals("ACCEPTED")&&user.equals(i.get("accepted_by"))){if(!Boolean.TRUE.equals(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM organization_memberships WHERE organization_id=? AND user_id=? AND status='ACTIVE')",Boolean.class,org,user)))throw unavailable();return preview(token,jwt);}
  if(!effective((String)i.get("status"),((Timestamp)i.get("expires_at")).toInstant()).equals("PENDING"))throw new DomainException(409,"INVITATION_NOT_PENDING","This invitation has expired or is no longer pending.");
  if(sql.queryForObject("SELECT count(*) FROM organization_memberships WHERE organization_id=? AND user_id=?",Integer.class,org,user)>0)throw new DomainException(409,"MEMBERSHIP_EXISTS","Your account already has a membership here. Ask an administrator to review its status or employee linkage.");
  UUID employee=(UUID)i.get("employee_id");if(employee!=null){var e=sql.queryForMap("SELECT user_id,active FROM employees WHERE id=? AND organization_id=? FOR UPDATE",employee,org);if(e.get("user_id")!=null||!Boolean.TRUE.equals(e.get("active")))throw new DomainException(409,"EMPLOYEE_UNAVAILABLE","The employee profile is no longer available for this invitation.");}
  sql.update("INSERT INTO organization_memberships(id,organization_id,user_id,role,status) VALUES (?,?,?,?,'ACTIVE')",UUID.randomUUID(),org,user,i.get("role"));
  if(employee!=null)sql.update("UPDATE employees SET user_id=?,version=version+1,updated_at=? WHERE id=? AND organization_id=?",user,Timestamp.from(clock.instant()),employee,org);
  sql.update("UPDATE organization_invitations SET status='ACCEPTED',accepted_by=?,accepted_at=?,updated_at=? WHERE id=?",user,Timestamp.from(clock.instant()),Timestamp.from(clock.instant()),id);
  sql.update("INSERT INTO organization_admin_audit(id,organization_id,actor_user_id,action,resource_id,details) VALUES (?,?,?,'INVITATION_ACCEPTED',?,'{}')",UUID.randomUUID(),org,user,id);return preview(token,jwt);}
 /** Called outside the claim transaction. Only the gateway receives the transient bearer link. */
 public EmailMessage delivery(UUID notificationId,UUID org){
  var rows=sql.queryForList("SELECT i.*,n.recipient_email,n.subject,n.body,o.name organization_name FROM notifications n JOIN organization_invitations i ON i.id=n.invitation_id AND i.organization_id=n.organization_id JOIN organizations o ON o.id=i.organization_id WHERE n.id=? AND n.organization_id=? AND i.status='PENDING' AND i.expires_at>? AND o.status='ACTIVE'",notificationId,org,Timestamp.from(clock.instant()));
  if(rows.isEmpty())throw new EmailDeliveryException(EmailDeliveryException.Failure.CONTENT);var r=rows.getFirst();String token=tokens.token((UUID)r.get("id"));if(!tokens.hash(token).equals(r.get("token_hash")))throw new EmailDeliveryException(EmailDeliveryException.Failure.CONTENT);
  String body="You have been invited to "+r.get("organization_name")+" as "+r.get("role")+".\n\nSign in or register using this email address and verify it before accepting.\n"+mail.publicBaseUrl()+"/invitations/accept#token="+token+"\n\nExpires: "+((Timestamp)r.get("expires_at")).toInstant().atZone(mail.zone())+"\nIf you did not expect this invitation, ignore it.\n";
  return new EmailMessage(notificationId,(String)r.get("recipient_email"),(String)r.get("subject"),body);
 }
}
