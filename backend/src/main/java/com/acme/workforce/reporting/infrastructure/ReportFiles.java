package com.acme.workforce.reporting.infrastructure;
import com.acme.workforce.reporting.api.ReportingDtos.*;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
@Component
public class ReportFiles {
 private static final String[] DETAIL_HEADERS={"Employee Number","Employee Name","Email","Manager","Year","Month","Project Code","Project Name","Work Date","Hours","Description","Timesheet Status","Submitted At","Approval Decision","Approval Timestamp","SAP Export Status","SAP External Reference"};
 private List<Object[]> detailRows(ReportSnapshot s){return s.details().stream().map(d->new Object[]{d.employeeNumber(),d.employeeName(),d.email(),d.managerName(),d.year(),d.month(),d.projectCode(),d.projectName(),d.workDate(),d.hours(),d.description(),d.timesheetStatus(),d.submittedAt(),d.approvalDecision(),d.approvalTimestamp(),d.sapState(),d.sapExternalReference()}).toList();}
 public byte[] csv(ReportSnapshot snapshot){
  var out=new StringBuilder();csvRow(out,DETAIL_HEADERS);
  for(var row:detailRows(snapshot))csvRow(out,row);
  return out.toString().getBytes(StandardCharsets.UTF_8);
 }
 // RFC 4180 quoting; spreadsheet formula prefixes are neutralized only in text fields.
 private void csvRow(StringBuilder out,Object[] row){
  for(int i=0;i<row.length;i++){if(i>0)out.append(',');
   String value=row[i]==null?"":row[i] instanceof BigDecimal n?n.toPlainString():row[i].toString();
   if(row[i] instanceof String && (value.startsWith("\t") || value.startsWith("\r") || value.startsWith("\n") || value.stripLeading().matches("(?s)^[=+@-].*")))value="'"+value;
   out.append('"').append(value.replace("\"","\"\"")).append('"');
  }
  out.append("\r\n");
 }
 public byte[] xlsx(ReportSnapshot s){
  try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
   var summary=s.summary();
   table(book,"Summary",new String[]{"Metric","Value"},List.of(
    new Object[]{"Year",summary.year()},new Object[]{"Month",summary.month()},new Object[]{"Hours mode",s.filter().effectiveMode().name()},
    new Object[]{"Employee filter",s.filter().employee()},new Object[]{"Project filter",s.filter().project()},
    new Object[]{"Timesheet filter",s.filter().timesheetStatus()},new Object[]{"SAP filter",s.filter().sapStatus()},
    new Object[]{"Active Employees",summary.activeEmployeeCount()},new Object[]{"No Timesheet",summary.noTimesheetCount()},
    new Object[]{"Draft",summary.draftCount()},new Object[]{"Pending Approval",summary.pendingApprovalCount()},
    new Object[]{"Rejected",summary.rejectedCount()},new Object[]{"Approved",summary.approvedCount()},
    new Object[]{"SAP Not Exported",summary.sapNotExportedCount()},new Object[]{"SAP Queued",summary.sapQueuedCount()},
    new Object[]{"SAP Processing",summary.sapProcessingCount()},new Object[]{"SAP Succeeded",summary.sapSucceededCount()},
    new Object[]{"SAP Failed",summary.sapFailedCount()},new Object[]{"SAP Unknown",summary.sapUnknownCount()},
    new Object[]{"Total Recorded Hours",summary.totalRecordedHours()},new Object[]{"Total Approved Hours",summary.totalApprovedHours()},
    new Object[]{"Total SAP Exported Hours",summary.totalSapExportedHours()},
    new Object[]{"Scope","Summary and Employees are operational context; project/detail sheets use the selected approved-hours mode."}));
   table(book,"Employees",new String[]{"Employee Number","Employee Name","Email","Manager","Timesheet State","Total Hours","Submitted At","Approval Decision","Approval Timestamp","SAP State","SAP External Reference"},
    s.employees().stream().map(e->new Object[]{e.employeeNumber(),e.employeeName(),e.email(),e.managerName(),e.timesheetState(),e.totalHours(),e.submittedAt(),e.latestApprovalDecision(),e.latestApprovalTimestamp(),e.sapState(),e.sapExternalReference()}).toList());
   String hours=s.filter().effectiveMode()==com.acme.workforce.reporting.domain.ReportMode.SAP_EXPORTED?"SAP Exported Hours":"Approved Hours";
   table(book,"Project Totals",new String[]{"Project Code","Project Name",hours,"Employee Count"},
    s.projects().stream().map(p->new Object[]{p.projectCode(),p.projectName(),p.totalHours(),p.employeeCount()}).toList());
   table(book,"Employee Project Hours",new String[]{"Employee Number","Employee Name","Project Code","Project Name",hours},
    s.employeeProjects().stream().map(p->new Object[]{p.employeeNumber(),p.employeeName(),p.projectCode(),p.projectName(),p.totalHours()}).toList());
   table(book,"Approved Hour Details",DETAIL_HEADERS,detailRows(s));
   book.write(out);return out.toByteArray();
  }catch(IOException e){throw new IllegalStateException("Report workbook generation failed",e);}
 }
 private void table(XSSFWorkbook book,String name,String[] headers,List<Object[]> rows){
  var sheet=book.createSheet(name);var bold=book.createFont();bold.setBold(true);
  var header=book.createCellStyle();header.setFont(bold);
  var hours=book.createCellStyle();hours.setDataFormat(book.createDataFormat().getFormat("0.00"));
  var date=book.createCellStyle();date.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
  var time=book.createCellStyle();time.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss \"UTC\""));
  var top=sheet.createRow(0);
  for(int i=0;i<headers.length;i++){var c=top.createCell(i);c.setCellValue(headers[i]);c.setCellStyle(header);sheet.setColumnWidth(i,(headers[i].contains("Description")?48:28)*256);}
  for(int n=0;n<rows.size();n++){
   var row=sheet.createRow(n+1);var values=rows.get(n);
   for(int i=0;i<values.length;i++){var c=row.createCell(i);var v=values[i];if(v==null)continue;
    if(v instanceof BigDecimal number){c.setCellType(CellType.NUMERIC);c.getCTCell().setV(number.toPlainString());c.setCellStyle(hours);}
    else if(v instanceof Number number){c.setCellType(CellType.NUMERIC);c.getCTCell().setV(number.toString());}
    else if(v instanceof LocalDate d && d.getYear()>=1900){c.setCellValue(d.atStartOfDay());c.setCellStyle(date);}
    else if(v instanceof Instant t && t.atOffset(ZoneOffset.UTC).getYear()>=1900){c.setCellValue(LocalDateTime.ofInstant(t,ZoneOffset.UTC));c.setCellStyle(time);}
    else c.setCellValue(v.toString()); // Explicit STRING cells, never formulas.
   }
  }
  sheet.createFreezePane(0,1);sheet.setAutoFilter(new CellRangeAddress(0,rows.size(),0,headers.length-1));
 }
}
