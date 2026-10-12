package com.acme.workforce.notification.infrastructure;
import com.acme.workforce.notification.application.*;
import com.acme.workforce.notification.domain.*;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import java.sql.*;
import java.time.*;
import java.util.*;
@Repository
public class NotificationStore {
 private final JdbcTemplate sql;private final NotificationPolicy policy;private final Clock clock;
 public NotificationStore(JdbcTemplate sql,NotificationPolicy policy,@Qualifier("notificationClock") Clock clock){this.sql=sql;this.policy=policy;this.clock=clock;}
 public record Recipient(UUID organizationId,UUID id,String email,String name){}
 public record Claim(UUID organizationId,UUID id,NotificationType type,UUID recipientId,EmailMessage message,UUID token,int attempt,Instant started){}
 private Claim row(ResultSet r,int n)throws SQLException{
  var id=r.getObject("id",UUID.class);
  return new Claim(r.getObject("organization_id",UUID.class),id,NotificationType.valueOf(r.getString("type")),r.getObject("recipient_user_id",UUID.class),
   new EmailMessage(id,r.getString("recipient_email"),r.getString("subject"),r.getString("body")),
   r.getObject("claim_token",UUID.class),r.getInt("attempt_count"),r.getTimestamp("locked_at").toInstant());
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public boolean enqueue(NotificationType type,Recipient user,String key,NotificationTemplates.Content content){
  return enqueue(type,user,key,content,null);
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public boolean enqueue(NotificationType type,Recipient user,String key,NotificationTemplates.Content content,YearMonth month){
  var now=Timestamp.from(clock.instant());
  String organization=sql.queryForObject("SELECT name || ' (' || slug || ')' FROM organizations WHERE id=?",String.class,user.organizationId());
  String body="Organization: "+organization+"\nSelect this organization in the app before following the links below.\n\n"+content.body();
  return sql.update("""
   INSERT INTO notifications(organization_id,id,type,recipient_user_id,recipient_email,subject,body,status,deduplication_key,next_attempt_at,created_at,updated_at,business_year,business_month)
   VALUES (?,?,?,?,?,?,?,'PENDING',?,?,?,?,?,?) ON CONFLICT(organization_id,deduplication_key) DO NOTHING
   """,user.organizationId(),UUID.randomUUID(),type.name(),user.id(),user.email(),content.subject(),body,key,now,now,now,month==null?null:month.getYear(),month==null?null:month.getMonthValue())>0;
 }
 public List<Recipient> financeRecipients(UUID organizationId){
  return sql.query("""
   SELECT u.id,u.email,u.display_name FROM app_users u JOIN organization_memberships m ON m.user_id=u.id JOIN organizations o ON o.id=m.organization_id LEFT JOIN employees e ON e.user_id=u.id AND e.organization_id=m.organization_id
   WHERE m.organization_id=? AND m.status='ACTIVE' AND o.status='ACTIVE' AND m.role IN ('FINANCE','ADMIN') AND (e.id IS NULL OR e.active) ORDER BY u.id
   """,(r,n)->new Recipient(organizationId,r.getObject("id",UUID.class),r.getString("email"),r.getString("display_name")),organizationId);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public List<Claim> claim(String worker){
  var now=clock.instant();
  var expired=sql.query("SELECT * FROM notifications WHERE organization_id IN (SELECT id FROM organizations WHERE status='ACTIVE') AND status='PROCESSING' AND locked_at<? ORDER BY locked_at,id LIMIT ? FOR UPDATE SKIP LOCKED",
   this::row,Timestamp.from(now.minus(policy.lease())),policy.batchSize());
  for(var c:expired)failure(c,"EMAIL_LEASE_EXPIRED","Delivery was not confirmed before the processing lease expired.",true,now);
  var ids=sql.queryForList("SELECT id FROM notifications WHERE organization_id IN (SELECT id FROM organizations WHERE status='ACTIVE') AND status='PENDING' AND next_attempt_at<=? ORDER BY next_attempt_at,id LIMIT ? FOR UPDATE SKIP LOCKED",UUID.class,Timestamp.from(now),policy.batchSize());
  var claims=new ArrayList<Claim>();
  for(var id:ids){var claim=sql.queryForObject("""
   UPDATE notifications SET status='PROCESSING',attempt_count=attempt_count+1,next_attempt_at=NULL,
   locked_at=?,locked_by=?,claim_token=?,updated_at=? WHERE id=? RETURNING *
   """,this::row,Timestamp.from(now),worker,UUID.randomUUID(),Timestamp.from(now),id);
   if(relevant(id))claims.add(claim);
   else{
    history(claim,"CANCELLED","NOTIFICATION_NO_LONGER_REQUIRED",now);
    sql.update("UPDATE notifications SET status='CANCELLED',locked_at=NULL,locked_by=NULL,claim_token=NULL WHERE id=?",id);
   }
  }
  return List.copyOf(claims);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public boolean finish(Claim c,boolean accepted,EmailDeliveryException.Failure error){
  var rows=sql.queryForList("SELECT id FROM notifications WHERE id=? AND organization_id=? AND status='PROCESSING' AND claim_token=? FOR UPDATE",UUID.class,c.id(),c.organizationId(),c.token());
  if(rows.isEmpty())return false;
  var now=clock.instant();if(now.isBefore(c.started()))now=c.started();
  if(accepted){
   history(c,"SENT",null,now);
   sql.update("""
    UPDATE notifications SET status='SENT',sent_at=?,last_error_code=NULL,last_error_message=NULL,
    locked_at=NULL,locked_by=NULL,claim_token=NULL,updated_at=? WHERE id=?
    """,Timestamp.from(now),Timestamp.from(now),c.id());
  }else{
   if(error==null)error=EmailDeliveryException.Failure.UNKNOWN;
   failure(c,error.code,error.message,error.retryable,now);
  }
  return true;
 }
 private boolean relevant(UUID id){
  return Boolean.TRUE.equals(sql.queryForObject("""
   SELECT EXISTS(SELECT 1 FROM organization_memberships membership JOIN organizations org ON org.id=membership.organization_id WHERE membership.organization_id=n.organization_id AND membership.user_id=n.recipient_user_id AND membership.status='ACTIVE' AND org.status='ACTIVE') AND CASE
    WHEN n.type='TIMESHEET_REMINDER' THEN EXISTS(
     SELECT 1 FROM employees e LEFT JOIN timesheets t ON t.employee_id=e.id AND t.year=n.business_year AND t.month=n.business_month
     WHERE e.organization_id=n.organization_id AND e.user_id=n.recipient_user_id AND e.active AND (t.id IS NULL OR t.status IN ('DRAFT','REJECTED')))
    WHEN n.type='APPROVAL_REMINDER' THEN EXISTS(
     SELECT 1 FROM employees m JOIN app_users u ON u.id=m.user_id JOIN employees e ON e.manager_id=m.id
     JOIN timesheets t ON t.employee_id=e.id
     WHERE m.organization_id=n.organization_id AND m.user_id=n.recipient_user_id AND m.active AND EXISTS(SELECT 1 FROM organization_memberships mbr WHERE mbr.organization_id=n.organization_id AND mbr.user_id=u.id AND mbr.role='MANAGER' AND mbr.status='ACTIVE') AND e.id<>m.id
     AND t.status='PENDING_APPROVAL' AND t.year=n.business_year AND t.month=n.business_month)
    WHEN n.type IN ('SAP_EXPORT_FAILED','SAP_EXPORT_UNKNOWN','FINANCE_MONTHLY_REMINDER') THEN EXISTS(
     SELECT 1 FROM app_users u JOIN organization_memberships m ON m.user_id=u.id LEFT JOIN employees e ON e.user_id=u.id AND e.organization_id=m.organization_id WHERE u.id=n.recipient_user_id
     AND m.organization_id=n.organization_id AND m.status='ACTIVE' AND m.role IN ('FINANCE','ADMIN') AND (e.id IS NULL OR e.active))
    ELSE true END FROM notifications n WHERE n.id=?
   """,Boolean.class,id));
 }
 private void failure(Claim c,String code,String message,boolean retryable,Instant now){
  boolean retry=retryable&&c.attempt()<policy.maxAttempts();
  history(c,retry?"RETRY_SCHEDULED":"FAILED",code,now);
  sql.update("""
   UPDATE notifications SET status=?,next_attempt_at=?,last_error_code=?,last_error_message=?,
   locked_at=NULL,locked_by=NULL,claim_token=NULL,updated_at=? WHERE id=?
   """,retry?"PENDING":"FAILED",retry?Timestamp.from(now.plus(policy.backoff(c.attempt()))):null,code,message,Timestamp.from(now),c.id());
 }
 private void history(Claim c,String outcome,String code,Instant finished){
  if(finished.isBefore(c.started()))finished=c.started();
  sql.update("""
   INSERT INTO notification_attempts(id,notification_id,attempt_number,started_at,finished_at,outcome,safe_error_code,duration_ms,created_at)
   VALUES (?,?,?,?,?,?,?,?,?)
   """,UUID.randomUUID(),c.id(),c.attempt(),Timestamp.from(c.started()),Timestamp.from(finished),outcome,code,Duration.between(c.started(),finished).toMillis(),Timestamp.from(finished));
 }
}
