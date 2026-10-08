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
 public record Recipient(UUID id,String email,String name){}
 public record Claim(UUID id,NotificationType type,UUID recipientId,EmailMessage message,UUID token,int attempt,Instant started){}
 private Claim row(ResultSet r,int n)throws SQLException{
  var id=r.getObject("id",UUID.class);
  return new Claim(id,NotificationType.valueOf(r.getString("type")),r.getObject("recipient_user_id",UUID.class),
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
  return sql.update("""
   INSERT INTO notifications(id,type,recipient_user_id,recipient_email,subject,body,status,deduplication_key,next_attempt_at,created_at,updated_at,business_year,business_month)
   VALUES (?,?,?,?,?,?,'PENDING',?,?,?,?,?,?) ON CONFLICT(deduplication_key) DO NOTHING
   """,UUID.randomUUID(),type.name(),user.id(),user.email(),content.subject(),content.body(),key,now,now,now,month==null?null:month.getYear(),month==null?null:month.getMonthValue())>0;
 }
 public List<Recipient> financeRecipients(){
  return sql.query("""
   SELECT u.id,u.email,u.display_name FROM app_users u LEFT JOIN employees e ON e.user_id=u.id
   WHERE u.role IN ('FINANCE','ADMIN') AND (e.id IS NULL OR e.active) ORDER BY u.id
   """,(r,n)->new Recipient(r.getObject("id",UUID.class),r.getString("email"),r.getString("display_name")));
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public List<Claim> claim(String worker){
  var now=clock.instant();
  var expired=sql.query("SELECT * FROM notifications WHERE status='PROCESSING' AND locked_at<? ORDER BY locked_at,id LIMIT ? FOR UPDATE SKIP LOCKED",
   this::row,Timestamp.from(now.minus(policy.lease())),policy.batchSize());
  for(var c:expired)failure(c,"EMAIL_LEASE_EXPIRED","Delivery was not confirmed before the processing lease expired.",true,now);
  var ids=sql.queryForList("SELECT id FROM notifications WHERE status='PENDING' AND next_attempt_at<=? ORDER BY next_attempt_at,id LIMIT ? FOR UPDATE SKIP LOCKED",UUID.class,Timestamp.from(now),policy.batchSize());
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
  var rows=sql.queryForList("SELECT id FROM notifications WHERE id=? AND status='PROCESSING' AND claim_token=? FOR UPDATE",UUID.class,c.id(),c.token());
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
   SELECT CASE
    WHEN n.type='TIMESHEET_REMINDER' THEN EXISTS(
     SELECT 1 FROM employees e LEFT JOIN timesheets t ON t.employee_id=e.id AND t.year=n.business_year AND t.month=n.business_month
     WHERE e.user_id=n.recipient_user_id AND e.active AND (t.id IS NULL OR t.status IN ('DRAFT','REJECTED')))
    WHEN n.type='APPROVAL_REMINDER' THEN EXISTS(
     SELECT 1 FROM employees m JOIN app_users u ON u.id=m.user_id JOIN employees e ON e.manager_id=m.id
     JOIN timesheets t ON t.employee_id=e.id
     WHERE m.user_id=n.recipient_user_id AND m.active AND u.role='MANAGER' AND e.id<>m.id
     AND t.status='PENDING_APPROVAL' AND t.year=n.business_year AND t.month=n.business_month)
    WHEN n.type IN ('SAP_EXPORT_FAILED','SAP_EXPORT_UNKNOWN','FINANCE_MONTHLY_REMINDER') THEN EXISTS(
     SELECT 1 FROM app_users u LEFT JOIN employees e ON e.user_id=u.id WHERE u.id=n.recipient_user_id
     AND u.role IN ('FINANCE','ADMIN') AND (e.id IS NULL OR e.active))
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
