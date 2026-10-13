package com.acme.workforce.organization.application;
import com.acme.workforce.common.application.DomainException;
import com.acme.workforce.identity.domain.UserRole;
import org.springframework.jdbc.core.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.constraints.*;
import java.util.*;
@Service @Transactional(readOnly=true)
public class MembershipAdministration {
 private final JdbcTemplate sql;private final AdministrationAccess access;
 public MembershipAdministration(JdbcTemplate sql,AdministrationAccess access){this.sql=sql;this.access=access;}
 public enum Status {ACTIVE,SUSPENDED,REVOKED}
 public record Change(@NotNull UserRole role,@NotNull Status status,@NotNull @Min(0) Long version){}
 public record Member(UUID id,UUID userId,String displayName,String email,String role,String status,UUID employeeId,String employeeNumber,long version){}
 private static final String SELECT="SELECT m.*,u.display_name,u.email,e.id employee_id,e.employee_number FROM organization_memberships m JOIN app_users u ON u.id=m.user_id LEFT JOIN employees e ON e.organization_id=m.organization_id AND e.user_id=m.user_id";
 private final RowMapper<Member> mapper=(r,n)->new Member(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),r.getString("display_name"),r.getString("email"),r.getString("role"),r.getString("status"),r.getObject("employee_id",UUID.class),r.getString("employee_number"),r.getLong("version"));
 public Page<Member> list(String search,String status,Pageable page){UUID org=access.read();String term="%"+search.strip().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  String where=" WHERE m.organization_id=? AND (u.display_name ILIKE ? ESCAPE '!' OR u.email ILIKE ? ESCAPE '!') AND (?='' OR m.status=?)";
  long count=sql.queryForObject("SELECT count(*) FROM organization_memberships m JOIN app_users u ON u.id=m.user_id"+where,Long.class,org,term,term,status,status);
  return new PageImpl<>(sql.query(SELECT+where+" ORDER BY u.display_name,m.id LIMIT ? OFFSET ?",mapper,org,term,term,status,status,page.getPageSize(),page.getOffset()),page,count);}
 @Transactional public Member change(UUID id,Change r){UUID org=access.write();var m=sql.query(SELECT+" WHERE m.organization_id=? AND m.id=?",mapper,org,id).stream().findFirst().orElseThrow(()->DomainException.notFound("Member"));AdministrationAccess.version(m.version(),r.version());
  if(m.role().equals("ADMIN")&&m.status().equals("ACTIVE")&&(r.role()!=UserRole.ADMIN||r.status()!=Status.ACTIVE)&&sql.queryForObject("SELECT count(*) FROM organization_memberships WHERE organization_id=? AND id<>? AND role='ADMIN' AND status='ACTIVE'",Integer.class,org,id)==0)throw new DomainException(409,"LAST_ACTIVE_ADMIN","Keep at least one active administrator in this organization.");
  sql.update("UPDATE organization_memberships SET role=?,status=?,version=version+1,updated_at=now() WHERE organization_id=? AND id=?",r.role().name(),r.status().name(),org,id);
  access.audit(org,"MEMBERSHIP_CHANGED",id,"{\"previousRole\":\""+m.role()+"\",\"previousStatus\":\""+m.status()+"\",\"role\":\""+r.role()+"\",\"status\":\""+r.status()+"\"}");
  return sql.queryForObject(SELECT+" WHERE m.organization_id=? AND m.id=?",mapper,org,id);}
}
