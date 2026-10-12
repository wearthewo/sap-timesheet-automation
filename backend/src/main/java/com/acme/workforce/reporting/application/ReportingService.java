package com.acme.workforce.reporting.application;
import com.acme.workforce.reporting.api.*;
import com.acme.workforce.reporting.api.ReportingDtos.*;
import com.acme.workforce.reporting.infrastructure.ReportingQueries;
import com.acme.workforce.identity.infrastructure.UserRepository;
import com.acme.workforce.identity.domain.UserRole;
import com.acme.workforce.common.application.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.slf4j.*;
import java.util.UUID;
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ReportingService {
 private final com.acme.workforce.organization.application.OrganizationAccess organization;
 private static final Logger log=LoggerFactory.getLogger(ReportingService.class);
 private final ReportingQueries queries;private final UserRepository users;private final int maxRows;
 public ReportingService(ReportingQueries queries,UserRepository users,@Value("${app.reporting.max-download-rows:10000}") int maxRows, com.acme.workforce.organization.application.OrganizationAccess organization) { this.organization=organization;
  if(maxRows<1 || maxRows>50000)throw new IllegalArgumentException("Reporting row limit must be between 1 and 50000");
  this.queries=queries;this.users=users;this.maxRows=maxRows;
 }
 private void authorize(UUID user){
  var role=organization.role(user);
  if(role!=UserRole.FINANCE && role!=UserRole.ADMIN)throw new DomainException(403,"REPORT_NOT_AUTHORIZED","Finance reporting requires FINANCE or ADMIN.");
 }
 private void log(UUID user,ReportFilter f,String type,long rows,long start){
  log.info("Report userId={} year={} month={} type={} mode={} format=json rows={} durationMs={}",user,f.year(),f.month(),type,f.effectiveMode(),rows,(System.nanoTime()-start)/1000000);
 }
 public MonthlyFinanceSummary summary(UUID user,ReportFilter f){long start=System.nanoTime();authorize(user);var result=queries.summary(f);log(user,f,"summary",result.activeEmployeeCount(),start);return result;}
 public Page<EmployeeMonthlyReportRow> employees(UUID user,ReportFilter f,Pageable page){long start=System.nanoTime();authorize(user);var result=queries.employees(f,page);log(user,f,"employees",result.getNumberOfElements(),start);return result;}
 public Page<ProjectMonthlyTotal> projects(UUID user,ReportFilter f,Pageable page){long start=System.nanoTime();authorize(user);var result=queries.projects(f,page);log(user,f,"projects",result.getNumberOfElements(),start);return result;}
 public Page<EmployeeProjectHours> employeeProjects(UUID user,ReportFilter f,Pageable page){long start=System.nanoTime();authorize(user);var result=queries.employeeProjects(f,page);log(user,f,"employee-projects",result.getNumberOfElements(),start);return result;}
 public ReportSnapshot snapshot(UUID user,ReportFilter f){
  authorize(user);
  var summary=queries.summary(f);if(summary.activeEmployeeCount()>maxRows)tooLarge();
  var details=queries.details(f,maxRows+1);if(details.size()>maxRows)tooLarge();
  var page=PageRequest.of(0,maxRows);
  var employees=queries.employees(f,page);var projects=queries.projects(f,page);var breakdown=queries.employeeProjects(f,page);
  if(projects.getTotalElements()>maxRows || breakdown.getTotalElements()>maxRows)tooLarge();
  return new ReportSnapshot(f,summary,employees.getContent(),projects.getContent(),breakdown.getContent(),details);
 }
 private void tooLarge(){throw new DomainException(413,"REPORT_TOO_LARGE","Report exceeds the configured row limit. Narrow the employee or project filters.");}
}
