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
 public ProjectService(ProjectRepository projects,AssignmentRepository assignments,EmployeeRepository employees, com.acme.workforce.organization.application.OrganizationAccess organization) { this.organization=organization; this.projects=projects; this.assignments=assignments; this.employees=employees; }
 public Page<ProjectView> list(Pageable page) { return projects.findByOrganizationId(organization.id(),page).map(ProjectView::from); }
 public ProjectView get(UUID id) { return ProjectView.from(requireProject(id)); }
 private Project requireProject(UUID id) { return projects.findByIdAndOrganizationId(id,organization.id()).orElseThrow(()->DomainException.notFound("Project")); }
 @Transactional
 public ProjectView create(WriteProject request) {
  var p=projects.saveAndFlush(new Project(organization.id(),request.code(),request.name(),request.description(),request.active()));
  log.info("Created project id={}",p.getId()); return ProjectView.from(p);
 }
 @Transactional
 public ProjectView update(UUID id,WriteProject request) {
  var p=requireProject(id); p.update(request.code(),request.name(),request.description(),request.active());
  projects.flush(); log.info("Updated project id={}",id); return ProjectView.from(p);
 }
 @Transactional
 public AssignmentView assign(WriteAssignment request) {
  if(request.validTo()!=null && request.validTo().isBefore(request.validFrom())) throw new DomainException(400,"INVALID_ASSIGNMENT_DATES","validTo must not be before validFrom");
  requireProject(request.projectId());
  if(!employees.existsByIdAndOrganizationId(request.employeeId(),organization.id())) throw DomainException.notFound("Employee");
  var a=assignments.saveAndFlush(new ProjectAssignment(request.employeeId(),request.projectId(),request.validFrom(),request.validTo(),request.active()));
  log.info("Created project assignment id={}",a.getId()); return AssignmentView.from(a);
 }
 public Page<ProjectView> assigned(UUID userId,Pageable page) {
  return employees.findByUserIdAndOrganizationId(userId,organization.id()).filter(e->e.isActive())
   .map(e->projects.assigned(e.getId(),LocalDate.now(ZoneOffset.UTC),page).map(ProjectView::from))
   .orElseGet(()->Page.empty(page));
 }
}
