package com.acme.workforce.employee.application;
import com.acme.workforce.employee.api.EmployeeAdminDtos.*;
import com.acme.workforce.organization.application.AdministrationAccess;
import com.acme.workforce.common.application.DomainException;
import org.springframework.jdbc.core.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @Transactional(readOnly=true)
public class EmployeeAdministration {
 private final JdbcTemplate sql;private final AdministrationAccess access;
 public EmployeeAdministration(JdbcTemplate sql,AdministrationAccess access){this.sql=sql;this.access=access;}
 private static final String SELECT="""
 SELECT e.*,coalesce(e.display_name,u.display_name,'Unlinked employee') name,u.email,coalesce(m.display_name,mu.display_name) manager_name,
 (SELECT CASE WHEN i.status='PENDING' AND i.expires_at<=now() THEN 'EXPIRED' ELSE i.status END FROM organization_invitations i WHERE i.employee_id=e.id ORDER BY i.created_at DESC,i.id DESC LIMIT 1) invitation_status
 FROM employees e LEFT JOIN app_users u ON u.id=e.user_id LEFT JOIN employees m ON m.id=e.manager_id LEFT JOIN app_users mu ON mu.id=m.user_id
 """;
 private final RowMapper<View> mapper=(r,n)->new View(r.getObject("id",UUID.class),r.getString("employee_number"),r.getString("name"),r.getBoolean("active"),r.getObject("manager_id",UUID.class),r.getString("manager_name"),r.getObject("user_id",UUID.class),r.getString("email"),r.getLong("version"),r.getString("invitation_status"));
 public Page<View> list(String search,Boolean active,String sort,Pageable page){
  UUID org=access.read();String term="%"+search.strip().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  String where=" WHERE e.organization_id=? AND (e.employee_number ILIKE ? ESCAPE '!' OR coalesce(e.display_name,u.display_name,'') ILIKE ? ESCAPE '!')"+(active==null?"":" AND e.active="+active);
  long count=sql.queryForObject("SELECT count(*) FROM employees e LEFT JOIN app_users u ON u.id=e.user_id"+where,Long.class,org,term,term);
  String order=switch(sort){case "name"->"name,e.id";case "number-desc"->"e.employee_number DESC,e.id";default->"e.employee_number,e.id";};
  return new PageImpl<>(sql.query(SELECT+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",mapper,org,term,term,page.getPageSize(),page.getOffset()),page,count);
 }
 public View get(UUID id){return get(access.read(),id);}
 private View get(UUID org,UUID id){return sql.query(SELECT+" WHERE e.organization_id=? AND e.id=?",mapper,org,id).stream().findFirst().orElseThrow(()->DomainException.notFound("Employee"));}
 private void manager(UUID org,UUID id,UUID manager){
  if(manager==null)return;var m=get(org,manager);
  if(manager.equals(id))throw new DomainException(409,"MANAGER_CYCLE","An employee cannot manage themselves.");
  if(!m.active())throw new DomainException(409,"MANAGER_INACTIVE","Choose an active manager.");
  if(m.userId()!=null && !Boolean.TRUE.equals(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM organization_memberships WHERE organization_id=? AND user_id=? AND status='ACTIVE' AND role='MANAGER')",Boolean.class,org,m.userId())))throw new DomainException(409,"MANAGER_ROLE_REQUIRED","The selected manager must have an active MANAGER membership.");
 }
 private void member(UUID org,UUID user){if(user!=null&&!Boolean.TRUE.equals(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM organization_memberships WHERE organization_id=? AND user_id=? AND status='ACTIVE')",Boolean.class,org,user)))throw DomainException.notFound("Member");}
 private void names(String number,String name){if(number.isBlank()||name.isBlank()||number.codePoints().anyMatch(Character::isISOControl)||name.codePoints().anyMatch(Character::isISOControl))throw new DomainException(400,"VALIDATION_ERROR","Employee number and name must not be blank or contain control characters.");}
 @Transactional public View create(Write r){UUID org=access.write(),id=UUID.randomUUID();names(r.employeeNumber(),r.displayName());member(org,r.userId());manager(org,id,r.managerId());
  sql.update("INSERT INTO employees(id,organization_id,user_id,employee_number,display_name,manager_id,active) VALUES (?,?,?,?,?,?,?)",id,org,r.userId(),r.employeeNumber().strip(),r.displayName().strip(),r.managerId(),r.active());access.audit(org,"EMPLOYEE_CREATED",id,"{}");return get(org,id);}
 @Transactional public View edit(UUID id,Edit r){UUID org=access.write();var current=get(org,id);AdministrationAccess.version(current.version(),r.version());names(r.employeeNumber(),r.displayName());manager(org,id,r.managerId());
  sql.update("UPDATE employees SET employee_number=?,display_name=?,manager_id=?,version=version+1,updated_at=now() WHERE id=? AND organization_id=?",r.employeeNumber().strip(),r.displayName().strip(),r.managerId(),id,org);access.audit(org,"EMPLOYEE_UPDATED",id,"{}");return get(org,id);}
 @Transactional public View state(UUID id,State r){UUID org=access.write();var current=get(org,id);AdministrationAccess.version(current.version(),r.version());sql.update("UPDATE employees SET active=?,version=version+1,updated_at=now() WHERE id=? AND organization_id=?",r.active(),id,org);access.audit(org,r.active()?"EMPLOYEE_ACTIVATED":"EMPLOYEE_DEACTIVATED",id,"{}");return get(org,id);}
 @Transactional public View link(UUID id,Link r){UUID org=access.write();var current=get(org,id);AdministrationAccess.version(current.version(),r.version());member(org,r.userId());if(current.userId()!=null)throw new DomainException(409,"EMPLOYEE_ALREADY_LINKED","This employee already has an account.");
  if(Boolean.TRUE.equals(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM organization_invitations WHERE employee_id=? AND status='PENDING' AND expires_at>now())",Boolean.class,id)))throw new DomainException(409,"INVITATION_PENDING","Revoke the pending invitation before linking another account.");
  sql.update("UPDATE employees SET user_id=?,version=version+1,updated_at=now() WHERE id=? AND organization_id=?",r.userId(),id,org);access.audit(org,"EMPLOYEE_LINKED",id,"{}");return get(org,id);}
}
