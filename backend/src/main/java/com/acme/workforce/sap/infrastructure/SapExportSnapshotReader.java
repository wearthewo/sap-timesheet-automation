package com.acme.workforce.sap.infrastructure;
import com.acme.workforce.sap.domain.*;
import com.acme.workforce.common.application.DomainException;
import com.acme.workforce.identity.infrastructure.UserRepository;
import com.acme.workforce.identity.domain.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;
@Repository
public class SapExportSnapshotReader {
 private final com.acme.workforce.organization.application.OrganizationAccess organization;
 private final JdbcTemplate sql;private final UserRepository users;private final ObjectMapper json;private final int maxRows;
 public SapExportSnapshotReader(JdbcTemplate sql,UserRepository users,ObjectMapper json,@Value("${app.reporting.max-download-rows}") int maxRows, com.acme.workforce.organization.application.OrganizationAccess organization) { this.organization=organization;this.sql=sql;this.users=users;this.json=json;this.maxRows=maxRows;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public SapExportSnapshot read(UUID userId,UUID timesheetId){
  var role=organization.role(userId);
  if(role!=UserRole.FINANCE&&role!=UserRole.ADMIN)throw new DomainException(403,"SAP_EXPORT_NOT_AUTHORIZED","Only ADMIN and FINANCE may download SAP exports.");
  var rows=sql.queryForList("""
   SELECT t.status timesheet_status,x.id export_id,x.status export_status,x.external_reference,x.exported_at,j.created_at snapshot_created_at,
    octet_length(j.payload::text) payload_bytes,
    CASE WHEN octet_length(j.payload::text)<=8388608 THEN j.payload::text END payload
   FROM timesheets t LEFT JOIN sap_timesheet_exports x ON x.timesheet_id=t.id
   LEFT JOIN sap_export_jobs j ON j.export_id=x.id AND j.timesheet_id=t.id WHERE t.id=? AND t.organization_id=?
   """,timesheetId,organization.id());
  if(rows.isEmpty())throw new DomainException(404,"TIMESHEET_NOT_FOUND","Timesheet not found.");
  var row=rows.getFirst();
  if(!"APPROVED".equals(row.get("timesheet_status")))throw new DomainException(409,"SAP_EXPORT_NOT_ALLOWED","Only approved timesheets may have a successful-export workbook.");
  if(row.get("export_id")==null)throw new DomainException(404,"SAP_EXPORT_NOT_FOUND","No SAP export has been attempted.");
  if(!"SUCCEEDED".equals(row.get("export_status")))throw new DomainException(409,"SAP_EXPORT_NOT_SUCCEEDED","The SAP export must succeed before downloading its workbook.");
  if(row.get("payload_bytes") instanceof Number bytes && bytes.longValue()>8388608)throw tooLarge();
  try{
   String raw=(String)row.get("payload");String reference=(String)row.get("external_reference");
   if(raw==null||reference==null||reference.isBlank()||row.get("exported_at")==null||row.get("snapshot_created_at")==null)throw incomplete();
   // V10 backfilled legacy successes after the fact; those are not original export snapshots.
   if(((java.sql.Timestamp)row.get("snapshot_created_at")).toInstant().isAfter(((java.sql.Timestamp)row.get("exported_at")).toInstant()))throw incomplete();
   var tree=json.readTree(raw);
   for(String field:List.of("timesheetId","employeeNumber","year","month","entries"))if(!tree.hasNonNull(field))throw incomplete();
   if(!tree.get("entries").isArray())throw incomplete();
   if(tree.get("entries").size()>maxRows)throw tooLarge();
   var payload=json.treeToValue(tree,ApprovedTimesheetPayload.class);
   if(!timesheetId.equals(payload.timesheetId())||payload.year()<1||payload.year()>9999||payload.month()<1||payload.month()>12||!text(payload.employeeNumber())||payload.entries().isEmpty())throw incomplete();
   var month=YearMonth.of(payload.year(),payload.month());
   for(var entry:payload.entries()){
    if(entry==null||!text(entry.projectCode())||entry.workDate()==null||!month.equals(YearMonth.from(entry.workDate()))||
     entry.hours()==null||entry.hours().signum()<=0||entry.hours().scale()>2||entry.hours().precision()>15||
     (entry.description()!=null&&entry.description().length()>32767))throw incomplete();
   }
   return new SapExportSnapshot((UUID)row.get("export_id"),reference,((java.sql.Timestamp)row.get("exported_at")).toInstant(),payload);
  }catch(DomainException e){throw e;}catch(Exception e){throw incomplete();}
 }
 private boolean text(String text){return text!=null&&!text.isBlank()&&text.length()<=32767;}
 private DomainException incomplete(){return new DomainException(409,"SAP_EXPORT_SNAPSHOT_INCOMPLETE","The stored successful export is incomplete. Ask an administrator to review its history.");}
 private DomainException tooLarge(){return new DomainException(413,"SAP_EXPORT_TOO_LARGE","The stored SAP export exceeds the download size limit.");}
}
