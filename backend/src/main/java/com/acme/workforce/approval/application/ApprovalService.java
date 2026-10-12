package com.acme.workforce.approval.application;

import com.acme.workforce.approval.api.ApprovalDtos.*;
import com.acme.workforce.approval.domain.*;
import com.acme.workforce.approval.infrastructure.ApprovalRepository;
import com.acme.workforce.common.application.DomainException;
import com.acme.workforce.employee.domain.Employee;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import com.acme.workforce.identity.domain.UserRole;
import com.acme.workforce.identity.infrastructure.UserRepository;
import com.acme.workforce.timesheet.api.TimesheetDtos.Summary;
import com.acme.workforce.timesheet.application.TimesheetService;
import com.acme.workforce.timesheet.domain.*;
import com.acme.workforce.timesheet.infrastructure.TimesheetRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.slf4j.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ApprovalService {
 private final com.acme.workforce.organization.application.OrganizationAccess organization;
 private static final Logger log=LoggerFactory.getLogger(ApprovalService.class);
 private final UserRepository users;
 private final EmployeeRepository employees;
 private final TimesheetRepository sheets;
 private final ApprovalRepository approvals;
 private final TimesheetService timesheets; private final com.acme.workforce.notification.application.NotificationIntents notifications;
 public ApprovalService(UserRepository users,EmployeeRepository employees,TimesheetRepository sheets,ApprovalRepository approvals,TimesheetService timesheets,com.acme.workforce.notification.application.NotificationIntents notifications, com.acme.workforce.organization.application.OrganizationAccess organization) { this.organization=organization;
  this.users=users; this.employees=employees; this.sheets=sheets; this.approvals=approvals; this.timesheets=timesheets; this.notifications=notifications;
 }
 private Employee manager(UUID userId) {
  if(organization.role(userId)!=UserRole.MANAGER) throw unauthorized();
  var manager=employees.findByUserIdAndOrganizationId(userId,organization.id()).orElseThrow(()->new DomainException(403,"MANAGER_EMPLOYEE_PROFILE_NOT_FOUND","A manager employee profile is required"));
  if(!manager.isActive()) throw unauthorized();
  return manager;
 }
 private DomainException unauthorized() { return new DomainException(403,"APPROVAL_NOT_AUTHORIZED","You may review only your direct reports' timesheets as their manager"); }
 private Timesheet authorized(UUID userId,UUID id) {
  var manager=manager(userId);
  var sheet=sheets.findByIdAndOrganizationId(id,organization.id()).orElseThrow(()->new DomainException(404,"TIMESHEET_NOT_FOUND","Timesheet not found"));
  var employee=employees.findById(sheet.getEmployeeId()).orElseThrow(this::unauthorized);
  if(employee.getId().equals(manager.getId()) || !manager.getId().equals(employee.getManagerId())) throw unauthorized();
  return sheet;
 }
 public Page<PendingItem> pending(UUID userId,Pageable pageable) {
  var manager=manager(userId);
  var page=sheets.pendingForManager(manager.getId(),pageable);
  var employeeMap=employees.findAllById(page.stream().map(Timesheet::getEmployeeId).toList()).stream().collect(Collectors.toMap(Employee::getId,Function.identity()));
  var userMap=users.findAllById(employeeMap.values().stream().map(Employee::getUserId).toList()).stream().collect(Collectors.toMap(u->u.getId(),Function.identity()));
  return page.map(s->{var e=employeeMap.get(s.getEmployeeId()); return new PendingItem(new EmployeeView(e.getId(),e.getEmployeeNumber(),userMap.get(e.getUserId()).getDisplayName()),new Summary(s.getId(),s.getYear(),s.getMonth(),s.getStatus(),s.getTotalHours(),s.getSubmittedAt(),s.getVersion()));});
 }
 public Review review(UUID userId,UUID id) { return view(authorized(userId,id)); }
 private Review view(Timesheet sheet) {
  var employee=employees.findById(sheet.getEmployeeId()).orElseThrow(this::unauthorized);
  var user=users.findById(employee.getUserId()).orElseThrow(this::unauthorized);
  return new Review(new EmployeeView(employee.getId(),employee.getEmployeeNumber(),user.getDisplayName()),timesheets.describe(sheet));
 }
 @Transactional
 public Review decide(UUID userId,UUID id,long version,ApprovalDecision decision,String comment) {
  var sheet=authorized(userId,id);
  if(sheet.getVersion()!=version) throw new DomainException(409,"CONCURRENT_MODIFICATION","This timesheet changed in another session. Reload it before deciding.");
  sheet.requirePending();
  String normalized=comment==null ? null : comment.strip();
  if(decision==ApprovalDecision.REJECTED && (normalized==null || normalized.isBlank())) throw new DomainException(400,"REJECTION_COMMENT_REQUIRED","A rejection comment is required");
  if(normalized!=null && normalized.length()>2000) throw new DomainException(400,"VALIDATION_ERROR","Comment must not exceed 2000 characters");
  var manager=manager(userId);
  var decisionRecord=approvals.save(new TimesheetApproval(id,manager.getId(),decision,normalized));
  sheet.decide(decision==ApprovalDecision.APPROVED);
  sheets.flush(); // Status/version and append-only decision commit or roll back together.
  notifications.approval(decisionRecord.getId());
  log.info("Manager decision timesheetId={} managerEmployeeId={} decision={}",id,manager.getId(),decision);
  return view(sheet);
 }
}
