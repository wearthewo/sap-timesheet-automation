package com.acme.workforce.sap.application;
import com.acme.workforce.sap.domain.*;
import com.acme.workforce.sap.api.SapDtos.*;
import com.acme.workforce.sap.infrastructure.SapExportRepository;
import com.acme.workforce.common.application.DomainException;
import com.acme.workforce.identity.domain.UserRole;
import com.acme.workforce.identity.infrastructure.UserRepository;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import com.acme.workforce.timesheet.domain.*;
import com.acme.workforce.timesheet.infrastructure.*;
import com.acme.workforce.timesheet.application.TimesheetService;
import com.acme.workforce.timesheet.api.TimesheetDtos.Summary;
import com.acme.workforce.approval.api.ApprovalDtos.EmployeeView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class SapExportTransactions {
 private final com.acme.workforce.sap.infrastructure.SapJobStore jobs; private final com.acme.workforce.sap.infrastructure.SapJobQueries jobQueries; private final org.springframework.jdbc.core.JdbcTemplate sql;
 private final SapExportRepository exports; private final TimesheetRepository sheets;
 private final UserRepository users; private final EmployeeRepository employees; private final TimesheetService timesheets;
 public SapExportTransactions(SapExportRepository exports,TimesheetRepository sheets,UserRepository users,EmployeeRepository employees,TimesheetService timesheets,com.acme.workforce.sap.infrastructure.SapJobStore jobs,com.acme.workforce.sap.infrastructure.SapJobQueries jobQueries,org.springframework.jdbc.core.JdbcTemplate sql){
  this.jobs=jobs;this.jobQueries=jobQueries;this.sql=sql;this.exports=exports;this.sheets=sheets;this.users=users;this.employees=employees;this.timesheets=timesheets;
 }
 private void exporter(UUID userId){
  var role=users.findById(userId).orElseThrow(this::forbidden).getRole();
  if(role!=UserRole.ADMIN && role!=UserRole.FINANCE)throw forbidden();
 }
 private DomainException forbidden(){return new DomainException(403,"SAP_EXPORT_NOT_AUTHORIZED","Only ADMIN and FINANCE users may export timesheets.");}
 private Timesheet sheet(UUID id){return sheets.findById(id).orElseThrow(()->new DomainException(404,"TIMESHEET_NOT_FOUND","Timesheet not found"));}
 private EmployeeView employee(Timesheet s){
  var e=employees.findById(s.getEmployeeId()).orElseThrow();
  return new EmployeeView(e.getId(),e.getEmployeeNumber(),users.findById(e.getUserId()).orElseThrow().getDisplayName());
 }
 public Page<WorkItem> list(UUID userId,Pageable pageable){
  exporter(userId);
  var page=sheets.findByStatus(TimesheetStatus.APPROVED,pageable);
  var employeeMap=employees.findAllById(page.stream().map(Timesheet::getEmployeeId).toList()).stream().collect(Collectors.toMap(e->e.getId(),Function.identity()));
  var userMap=users.findAllById(employeeMap.values().stream().map(e->e.getUserId()).toList()).stream().collect(Collectors.toMap(u->u.getId(),Function.identity()));
  var exportMap=exports.findByTimesheetIdIn(page.stream().map(Timesheet::getId).toList()).stream().collect(Collectors.toMap(SapTimesheetExport::getTimesheetId,Function.identity()));
  var metadata=jobQueries.jobs(exportMap.values().stream().map(SapTimesheetExport::getId).toList());
  return page.map(s->{var e=employeeMap.get(s.getEmployeeId());var export=exportMap.get(s.getId());
   return new WorkItem(new EmployeeView(e.getId(),e.getEmployeeNumber(),userMap.get(e.getUserId()).getDisplayName()),
    new Summary(s.getId(),s.getYear(),s.getMonth(),s.getStatus(),s.getTotalHours(),s.getSubmittedAt(),s.getVersion()),export==null?null:ExportView.of(export,metadata.get(export.getId()),List.of()));});
 }
 public ExportDetail detail(UUID userId,UUID id){exporter(userId);var s=sheet(id);return new ExportDetail(employee(s),timesheets.describe(s),exports.findByTimesheetId(id).map(this::view).orElse(null));}
 public ExportView state(UUID userId,UUID id){
  var role=users.findById(userId).orElseThrow(this::forbidden).getRole();
  if(role!=UserRole.ADMIN && role!=UserRole.FINANCE){
   var current=employees.findByUserId(userId).orElseThrow(this::forbidden);
   var owner=employees.findById(sheet(id).getEmployeeId()).orElseThrow(this::forbidden);
   if(!current.isActive() || !(owner.getId().equals(current.getId()) || (role==UserRole.MANAGER && current.getId().equals(owner.getManagerId()))))throw forbidden();
  }
  return exports.findByTimesheetId(id).map(this::view).orElseThrow(()->new DomainException(404,"SAP_EXPORT_NOT_FOUND","No SAP export has been attempted."));
 }
 private ExportView view(SapTimesheetExport e){return ExportView.of(e,jobQueries.jobs(List.of(e.getId())).get(e.getId()),jobQueries.attempts(e.getId(),PageRequest.of(0,20)).getContent());}
 public Page<AttemptView> attempts(UUID userId,UUID id,Pageable page){var state=state(userId,id);return jobQueries.attempts(state.exportId(),page);}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public ExportView enqueue(UUID userId,UUID id,long version){
  exporter(userId);
  sql.queryForList("SELECT id FROM timesheets WHERE id=? FOR UPDATE",UUID.class,id);
  var s=sheet(id);
  if(s.getStatus()!=TimesheetStatus.APPROVED)throw new DomainException(409,"SAP_EXPORT_NOT_ALLOWED","Only approved timesheets may be exported.");
  if(s.getVersion()!=version)throw new DomainException(409,"CONCURRENT_MODIFICATION","This timesheet changed. Reload before exporting.");
  var existing=exports.findByTimesheetId(id);
  if(existing.isPresent())return view(existing.get());
  var detail=timesheets.describe(s);
  var payload=new ApprovedTimesheetPayload(id,employee(s).employeeNumber(),s.getYear(),s.getMonth(),
   detail.entries().stream().map(e->new ApprovedTimesheetPayload.Entry(e.project().code(),e.workDate(),e.hours(),e.description())).toList());
  var reserved=exports.saveAndFlush(new SapTimesheetExport(id));
  jobs.create(reserved.getId(),payload);
  return view(reserved);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void manual(UUID userId,UUID id,boolean reconcile){exporter(userId);jobs.manual(id,reconcile);}
}