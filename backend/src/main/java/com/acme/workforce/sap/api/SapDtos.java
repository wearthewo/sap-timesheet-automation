package com.acme.workforce.sap.api;
import com.acme.workforce.sap.domain.*;
import com.acme.workforce.approval.api.ApprovalDtos.EmployeeView;
import com.acme.workforce.timesheet.api.TimesheetDtos.*;
import java.time.Instant;
import java.util.*;
public final class SapDtos {
 private SapDtos(){}
 public record JobView(String status,String operation,int attemptCount,int executionCount,Instant lastAttemptAt,Instant nextAttemptAt,boolean retryEligible,boolean reconcileEligible){}
 public record AttemptView(UUID id,int attemptNumber,String operation,Instant startedAt,Instant finishedAt,String outcome,String safeErrorCode,long durationMs,String externalReference){}
 public record ExportView(UUID exportId,UUID timesheetId,SapExportStatus status,String externalReference,String failureCode,String failureMessage,Instant exportedAt,String idempotencyKey,JobView job,List<AttemptView> attempts){
  public static ExportView of(SapTimesheetExport e,JobView job,List<AttemptView> attempts){return new ExportView(e.getId(),e.getTimesheetId(),e.getStatus(),e.getExternalReference(),e.getFailureCode(),e.getFailureMessage(),e.getExportedAt(),"SAP_TIMESHEET:"+e.getTimesheetId(),job,attempts);}
 }
 public record WorkItem(EmployeeView employee,Summary timesheet,ExportView export){}
 public record ExportDetail(EmployeeView employee,Detail timesheet,ExportView export){}
}
