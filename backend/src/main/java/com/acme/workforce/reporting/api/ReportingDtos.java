package com.acme.workforce.reporting.api;
import com.acme.workforce.reporting.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class ReportingDtos {
 private ReportingDtos(){}
 public record MonthlyFinanceSummary(int year,int month,long activeEmployeeCount,long noTimesheetCount,
  long draftCount,long pendingApprovalCount,long rejectedCount,long approvedCount,
  long sapNotExportedCount,long sapQueuedCount,long sapProcessingCount,long sapSucceededCount,long sapFailedCount,long sapUnknownCount,
  @io.swagger.v3.oas.annotations.media.Schema(type="string", example="6.50") @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal totalRecordedHours,@io.swagger.v3.oas.annotations.media.Schema(type="string", example="6.50") @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal totalApprovedHours,@io.swagger.v3.oas.annotations.media.Schema(type="string", example="6.50") @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal totalSapExportedHours){}
 public record EmployeeMonthlyReportRow(UUID employeeId,String employeeNumber,String employeeName,String email,
  UUID managerEmployeeId,String managerName,UUID timesheetId,ReportingTimesheetState timesheetState,
  @io.swagger.v3.oas.annotations.media.Schema(type="string", example="6.50") @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal totalHours,Instant submittedAt,String latestApprovalDecision,Instant latestApprovalTimestamp,
  ReportingSapState sapState,String sapExternalReference,Instant sapExportedAt){}
 public record ProjectMonthlyTotal(UUID projectId,String projectCode,String projectName,@io.swagger.v3.oas.annotations.media.Schema(type="string", example="6.50") @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal totalHours,long employeeCount){}
 public record EmployeeProjectHours(UUID employeeId,String employeeNumber,String employeeName,
  UUID projectId,String projectCode,String projectName,@io.swagger.v3.oas.annotations.media.Schema(type="string", example="6.50") @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal totalHours){}
 public record ApprovedHourDetail(String employeeNumber,String employeeName,String email,String managerName,
  int year,int month,String projectCode,String projectName,LocalDate workDate,@io.swagger.v3.oas.annotations.media.Schema(type="string", example="6.50") @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal hours,String description,
  String timesheetStatus,Instant submittedAt,String approvalDecision,Instant approvalTimestamp,
  ReportingSapState sapState,String sapExternalReference){}
 public record ReportSnapshot(ReportFilter filter,MonthlyFinanceSummary summary,List<EmployeeMonthlyReportRow> employees,
  List<ProjectMonthlyTotal> projects,List<EmployeeProjectHours> employeeProjects,List<ApprovedHourDetail> details){}
}
