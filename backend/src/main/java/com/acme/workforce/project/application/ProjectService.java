package com.acme.workforce.project.application;

import com.acme.workforce.project.infrastructure.*;
import com.acme.workforce.project.domain.*;
import com.acme.workforce.project.api.ProjectDtos.*;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import com.acme.workforce.common.application.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.slf4j.*;
import java.util.UUID;
import java.time.*;
@Service
@Transactional(readOnly=true)
public class ProjectService {
 private final com.acme.workforce.organization.application.OrganizationAccess organization;
 private static final Logger log=LoggerFactory.getLogger(ProjectService.class);
 private final ProjectRepository projects; private final AssignmentRepository assignments; private final EmployeeRepository employees;
 private final com.acme.workforce.organization.application.AdministrationAccess access;
 private final org.springframework.jdbc.core.JdbcTemplate sql;
 public ProjectService(ProjectRepository projects,AssignmentRepository assignments,EmployeeRepository employees, com.acme.workforce.organization.application.OrganizationAccess organization,com.acme.workforce.organization.application.AdministrationAccess access,org.springframework.jdbc.core.JdbcTemplate sql) { this.organization=organization; this.projects=projects; this.assignments=assignments; this.employees=employees;this.access=access;this.sql=sql; }
 public Page<ProjectView> list(Pageable page) { return projects.findByOrganizationId(organization.id(),page).map(ProjectView::from); }
 public ProjectView get(UUID id) { return ProjectView.from(requireProject(id)); }
 private Project requireProject(UUID id) { return projects.findByIdAndOrganizationId(id,organization.id()).orElseThrow(()->DomainException.notFound("Project")); }
 @Transactional
 public ProjectView create(WriteProject request) {
  var org=access.write();
  var p=projects.saveAndFlush(new Project(organization.id(),request.code(),request.name(),request.description(),request.active()));
  access.audit(org,"PROJECT_CREATED",p.getId(),"{}");
  log.info("Created project id={}",p.getId()); return ProjectView.from(p);
 }
 @Transactional
 public ProjectView update(UUID id,WriteProject request) {
  var org=access.write();
  var p=requireProject(id); p.update(request.code(),request.name(),request.description(),request.active());
  projects.flush(); access.audit(org,"PROJECT_UPDATED",id,"{}");log.info("Updated project id={}",id); return ProjectView.from(p);
 }
 @Transactional
 public AssignmentView assign(WriteAssignment request) {
  var org=access.write();
  if(request.validTo()!=null && request.validTo().isBefore(request.validFrom())) throw new DomainException(400,"INVALID_ASSIGNMENT_DATES","validTo must not be before validFrom");
  requireProject(request.projectId());
  if(!employees.existsByIdAndOrganizationId(request.employeeId(),organization.id())) throw DomainException.notFound("Employee");
  var a=assignments.saveAndFlush(new ProjectAssignment(request.employeeId(),request.projectId(),request.validFrom(),request.validTo(),request.active()));
  access.audit(org,"ASSIGNMENT_CREATED",a.getId(),"{}");
  log.info("Created project assignment id={}",a.getId()); return AssignmentView.from(a);
 }
 public Page<ProjectView> assigned(UUID userId,Pageable page) {
  return employees.findByUserIdAndOrganizationId(userId,organization.id()).filter(e->e.isActive())
   .map(e->projects.assigned(e.getId(),LocalDate.now(ZoneOffset.UTC),page).map(ProjectView::from))
   .orElseGet(()->Page.empty(page));
 }
 @Transactional public ProjectView state(UUID id,boolean active){var org=access.write();var p=requireProject(id);p.update(p.getCode(),p.getName(),p.getDescription(),active);projects.flush();access.audit(org,active?"PROJECT_ACTIVATED":"PROJECT_ARCHIVED",id,"{}");return ProjectView.from(p);}
 @Transactional public AssignmentView updateAssignment(UUID id,WriteAssignment r){var org=access.write();
  if(!Boolean.TRUE.equals(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM project_assignments WHERE id=? AND organization_id=?)",Boolean.class,id,org)))throw DomainException.notFound("Assignment");
  var a=assignments.findById(id).orElseThrow(()->DomainException.notFound("Assignment"));
  if(!a.getEmployeeId().equals(r.employeeId())||!a.getProjectId().equals(r.projectId()))throw new DomainException(409,"ASSIGNMENT_OWNERSHIP_IMMUTABLE","Create a new assignment to change the employee or project.");
  if(r.validTo()!=null&&r.validTo().isBefore(r.validFrom()))throw new DomainException(400,"INVALID_ASSIGNMENT_DATES","validTo must not be before validFrom");
  a.update(r.validFrom(),r.validTo(),r.active());assignments.flush();access.audit(org,"ASSIGNMENT_UPDATED",id,"{}");return AssignmentView.from(a);
 }
 public Page<ProjectView> adminList(String search,Boolean active,String sort,Pageable page){var org=access.read();String term="%"+search.strip().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  String where=" WHERE organization_id=? AND (code ILIKE ? ESCAPE '!' OR name ILIKE ? ESCAPE '!')"+(active==null?"":" AND active="+active);
  long count=sql.queryForObject("SELECT count(*) FROM projects"+where,Long.class,org,term,term);
  String order=switch(sort){case "name"->"name,id";case "code-desc"->"code DESC,id";default->"code,id";};
  var rows=sql.query("SELECT * FROM projects"+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",(r,n)->new ProjectView(r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getString("description"),r.getBoolean("active"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant()),org,term,term,page.getPageSize(),page.getOffset());return new PageImpl<>(rows,page,count);
 }
 public record AdminAssignment(UUID id,UUID employeeId,String employeeNumber,String employeeName,UUID projectId,String projectCode,String projectName,LocalDate validFrom,LocalDate validTo,boolean active){}
 public Page<AdminAssignment> adminAssignments(UUID employee,UUID project,Pageable page){var org=access.read();
  if(employee!=null&&!employees.existsByIdAndOrganizationId(employee,org))throw DomainException.notFound("Employee");if(project!=null)requireProject(project);
  String where=" WHERE a.organization_id=?"+(employee==null?"":" AND a.employee_id=?")+(project==null?"":" AND a.project_id=?");var args=new java.util.ArrayList<Object>();args.add(org);if(employee!=null)args.add(employee);if(project!=null)args.add(project);
  long count=sql.queryForObject("SELECT count(*) FROM project_assignments a"+where,Long.class,args.toArray());args.add(page.getPageSize());args.add(page.getOffset());
  var rows=sql.query("SELECT a.*,e.employee_number,coalesce(e.display_name,u.display_name,'Unlinked employee') employee_name,p.code,p.name FROM project_assignments a JOIN employees e ON e.id=a.employee_id LEFT JOIN app_users u ON u.id=e.user_id JOIN projects p ON p.id=a.project_id"+where+" ORDER BY a.valid_from DESC,a.id LIMIT ? OFFSET ?",(r,n)->new AdminAssignment(r.getObject("id",UUID.class),r.getObject("employee_id",UUID.class),r.getString("employee_number"),r.getString("employee_name"),r.getObject("project_id",UUID.class),r.getString("code"),r.getString("name"),r.getDate("valid_from").toLocalDate(),r.getDate("valid_to")==null?null:r.getDate("valid_to").toLocalDate(),r.getBoolean("active")),args.toArray());return new PageImpl<>(rows,page,count);
 }
}
