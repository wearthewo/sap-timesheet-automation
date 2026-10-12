package com.acme.workforce.sap.infrastructure;

import com.acme.workforce.sap.application.SapWorkerPolicy;
import com.acme.workforce.sap.domain.*;
import com.acme.workforce.common.application.DomainException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class SapJobStore {
 private final JdbcTemplate sql;private final ObjectMapper json;private final SapWorkerPolicy policy; private final com.acme.workforce.notification.application.NotificationIntents notifications;
 public SapJobStore(JdbcTemplate sql,ObjectMapper json,SapWorkerPolicy policy,com.acme.workforce.notification.application.NotificationIntents notifications){this.sql=sql;this.json=json;this.policy=policy;this.notifications=notifications;}
 public record Claim(UUID organizationId,UUID id,UUID exportId,UUID timesheetId,String operation,UUID token,int execution,int attempts,int cycleAttempts,int reconciliations,Instant started,ApprovedTimesheetPayload payload){}
 private Claim claimRow(ResultSet r,int row)throws SQLException {
  try{return new Claim(r.getObject("organization_id",UUID.class),r.getObject("id",UUID.class),r.getObject("export_id",UUID.class),r.getObject("timesheet_id",UUID.class),r.getString("operation"),r.getObject("claim_token",UUID.class),r.getInt("execution_count"),r.getInt("attempt_count"),r.getInt("cycle_attempts"),r.getInt("reconciliation_count"),r.getTimestamp("locked_at").toInstant(),json.readValue(r.getString("payload"),ApprovedTimesheetPayload.class));}
  catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Invalid stored SAP payload");}
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public void create(UUID exportId,ApprovedTimesheetPayload payload){
  try{sql.update("INSERT INTO sap_export_jobs(id,export_id,timesheet_id,status,operation,payload,next_attempt_at) VALUES (?,?,?,'PENDING','EXPORT',?::jsonb,now())",UUID.randomUUID(),exportId,payload.timesheetId(),json.writeValueAsString(payload));}
  catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Cannot snapshot SAP payload");}
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public List<Claim> claim(String worker){
  // Recover expired claims as reconciliation, never as an unconditional second POST.
  var stale=sql.query("SELECT * FROM sap_export_jobs WHERE organization_id IN (SELECT id FROM organizations WHERE status='ACTIVE') AND status='PROCESSING' AND locked_at < now() - (? * interval '1 millisecond') ORDER BY locked_at,id LIMIT ? FOR UPDATE SKIP LOCKED",this::claimRow,policy.lease().toMillis(),policy.batchSize());
  for(var c:stale){
   history(c,"ABANDONED","SAP_LEASE_EXPIRED",null);
   setExport(c,"UNKNOWN",null,"SAP_LEASE_EXPIRED","The worker lease expired. SAP acceptance must be reconciled.");
   schedule(c,"PENDING","RECONCILE",Instant.now(),"SAP_LEASE_EXPIRED",false);
  }
  var ids=sql.queryForList("SELECT id FROM sap_export_jobs WHERE organization_id IN (SELECT id FROM organizations WHERE status='ACTIVE') AND status='PENDING' AND next_attempt_at<=clock_timestamp() ORDER BY next_attempt_at,id LIMIT ? FOR UPDATE SKIP LOCKED",UUID.class,policy.batchSize());
  var result=new ArrayList<Claim>();
  for(var id:ids){
   var c=sql.queryForObject("""
    UPDATE sap_export_jobs SET status='PROCESSING',locked_at=clock_timestamp(),locked_by=?,claim_token=?,
    last_attempt_at=clock_timestamp(),execution_count=execution_count+1,
    attempt_count=attempt_count+CASE WHEN operation='EXPORT' THEN 1 ELSE 0 END,
    cycle_attempts=cycle_attempts+CASE WHEN operation='EXPORT' THEN 1 ELSE 0 END,
    reconciliation_count=CASE WHEN operation='RECONCILE' THEN reconciliation_count+1 ELSE 0 END,
    updated_at=clock_timestamp() WHERE id=? RETURNING *
    """,this::claimRow,worker,UUID.randomUUID(),id);
   if(c.operation().equals("EXPORT"))sql.update("UPDATE sap_timesheet_exports SET status='PROCESSING',updated_at=now() WHERE id=?",c.exportId());
   result.add(c);
  }
  return List.copyOf(result);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public boolean finish(Claim c,SapExportResult result,SapGatewayException.Failure failure,boolean missing){
  // A stale worker has no authority once its claim token has been replaced.
  var owns=sql.queryForList("SELECT id FROM sap_export_jobs WHERE id=? AND organization_id=? AND status='PROCESSING' AND claim_token=? FOR UPDATE",UUID.class,c.id(),c.organizationId(),c.token());
  if(owns.isEmpty())return false;
  if(result!=null){
   history(c,"SUCCEEDED",null,result.externalReference());setExport(c,"SUCCEEDED",result.externalReference(),null,null);
   schedule(c,"COMPLETED",c.operation(),Instant.now(),null,false);return true;
  }
  if(missing){
   history(c,"NOT_FOUND","SAP_NOT_FOUND",null);
   retryOrFail(c,"SAP_NOT_FOUND","SAP confirms this export identity does not exist.");
   return true;
  }
  if(failure==null)failure=SapGatewayException.Failure.UNKNOWN;
  if(c.operation().equals("RECONCILE")||failure.ambiguous){
   history(c,"UNKNOWN",failure.code,null);setExport(c,"UNKNOWN",null,failure.code,failure.message);
   boolean more=c.reconciliations()<policy.maxAttempts();
   // Permanent lookup auth/configuration errors require manual reconciliation after correction.
   if(c.operation().equals("RECONCILE") && (failure==SapGatewayException.Failure.AUTHENTICATION||failure==SapGatewayException.Failure.CONFIGURATION||failure==SapGatewayException.Failure.VALIDATION))more=false;
   schedule(c,more?"PENDING":"FAILED","RECONCILE",Instant.now().plus(policy.reconciliationInterval()),failure.code,false);
  } else if(failure==SapGatewayException.Failure.CONNECTION){
   history(c,"FAILED",failure.code,null);retryOrFail(c,failure.code,failure.message);
  } else {
   history(c,"FAILED",failure.code,null);setExport(c,"FAILED",null,failure.code,failure.message);
   schedule(c,"FAILED","EXPORT",Instant.now(),failure.code,false);
  }
  return true;
 }
 private void retryOrFail(Claim c,String code,String message){
  if(c.cycleAttempts()<policy.maxAttempts()){
   setExport(c,"QUEUED",null,code,message);
   schedule(c,"PENDING","EXPORT",Instant.now().plus(policy.backoff(c.cycleAttempts())),code,false);
  }else{
   setExport(c,"FAILED",null,code,"Retry limit reached. "+message);
   schedule(c,"FAILED","EXPORT",Instant.now(),code,true);
  }
 }
 private void setExport(Claim c,String status,String reference,String code,String message){
  String previous=sql.queryForObject("SELECT status FROM sap_timesheet_exports WHERE id=?",String.class,c.exportId());
  sql.update("UPDATE sap_timesheet_exports SET status=?,external_reference=?,exported_at=CASE WHEN ?='SUCCEEDED' THEN now() ELSE NULL END,failure_code=?,failure_message=?,updated_at=now() WHERE id=?",status,reference,status,code,message,c.exportId());
  if(!status.equals(previous))notifications.sap(c.exportId(),c.execution(),status);
 }
 private void schedule(Claim c,String status,String operation,Instant next,String code,boolean retry){
  sql.update("UPDATE sap_export_jobs SET status=?,operation=?,next_attempt_at=?,last_error_code=?,retry_eligible=?,locked_at=NULL,locked_by=NULL,claim_token=NULL,updated_at=now() WHERE id=?",status,operation,Timestamp.from(next),code,retry,c.id());
 }
 private void history(Claim c,String outcome,String code,String reference){
  var finished=Instant.now();if(finished.isBefore(c.started()))finished=c.started();
  sql.update("INSERT INTO sap_export_attempts(id,export_id,job_id,attempt_number,operation,started_at,finished_at,outcome,safe_error_code,duration_ms,external_reference) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
   UUID.randomUUID(),c.exportId(),c.id(),c.execution(),c.operation(),Timestamp.from(c.started()),Timestamp.from(finished),outcome,code,Duration.between(c.started(),finished).toMillis(),reference);
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public void manual(UUID timesheetId,boolean reconcile){
  var rows=sql.queryForList("SELECT id,export_id,status,retry_eligible FROM sap_export_jobs WHERE timesheet_id=? FOR UPDATE",timesheetId);
  if(rows.isEmpty())throw new DomainException(404,"SAP_EXPORT_NOT_FOUND","No SAP export exists.");
  var row=rows.getFirst();
  // Read the export after acquiring the job lock: a competing completion may have committed while we waited.
  String exportStatus=sql.queryForObject("SELECT status FROM sap_timesheet_exports WHERE id=?",String.class,row.get("export_id"));
  if("PROCESSING".equals(row.get("status")))throw new DomainException(409,"SAP_EXPORT_PROCESSING","An operation is already in progress.");
  boolean allowed=reconcile?"UNKNOWN".equals(exportStatus):"FAILED".equals(exportStatus)&&Boolean.TRUE.equals(row.get("retry_eligible"));
  if(!allowed)throw new DomainException(409,reconcile?"SAP_RECONCILIATION_NOT_ALLOWED":"SAP_RETRY_NOT_ALLOWED","This export is not eligible for this action.");
  sql.update("UPDATE sap_export_jobs SET status='PENDING',operation=?,cycle_attempts=CASE WHEN ? THEN cycle_attempts ELSE 0 END,reconciliation_count=0,next_attempt_at=now(),retry_eligible=false,updated_at=now() WHERE id=?",reconcile?"RECONCILE":"EXPORT",reconcile,row.get("id"));
  if(!reconcile)sql.update("UPDATE sap_timesheet_exports SET status='QUEUED',updated_at=now() WHERE timesheet_id=?",timesheetId);
 }
}
