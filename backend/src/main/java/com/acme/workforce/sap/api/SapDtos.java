package com.acme.workforce.sap.api;
import com.acme.workforce.sap.domain.*;
import com.acme.workforce.approval.api.ApprovalDtos.EmployeeView;
import com.acme.workforce.timesheet.api.TimesheetDtos.*;
import java.time.Instant;
import java.util.UUID;
public final class SapDtos {
 private SapDtos(){}
 public record ExportView(UUID exportId,UUID timesheetId,SapExportStatus status,String externalReference,String failureCode,String failureMessage,Instant exportedAt,String idempotencyKey){
  public static ExportView of(SapTimesheetExport e){return new ExportView(e.getId(),e.getTimesheetId(),e.getStatus(),e.getExternalReference(),e.getFailureCode(),e.getFailureMessage(),e.getExportedAt(),"SAP_TIMESHEET:"+e.getTimesheetId());}
 }
 public record WorkItem(EmployeeView employee,Summary timesheet,ExportView export){}
 public record ExportDetail(EmployeeView employee,Detail timesheet,ExportView export){}
}
