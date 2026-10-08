package com.acme.workforce.notification.application;
import com.acme.workforce.notification.domain.*;
import com.acme.workforce.notification.infrastructure.NotificationStore;
import com.acme.workforce.notification.infrastructure.NotificationStore.Recipient;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.*;
import java.time.*;
import java.util.*;
@Service
public class NotificationEvaluation {
 private static final Logger log=LoggerFactory.getLogger(NotificationEvaluation.class);
 private final JdbcTemplate sql;private final NotificationStore store;private final NotificationTemplates templates;private final NotificationPolicy policy;private final Clock clock;
 public NotificationEvaluation(JdbcTemplate sql,NotificationStore store,NotificationTemplates templates,NotificationPolicy policy,@Qualifier("notificationClock") Clock clock){this.sql=sql;this.store=store;this.templates=templates;this.policy=policy;this.clock=clock;}
 @Transactional
 public int evaluate(){
  var date=LocalDate.now(clock.withZone(policy.zone()));var month=YearMonth.from(date);int created=0;
  if(policy.due(date,policy.employeeDay()))created+=employees(date,month);
  created+=managers(date);
  if(policy.due(date,policy.financeDay()))created+=finance(month.minusMonths(1));
  log.info("Notification evaluation businessDate={} created={}",date,created);return created;
 }
 private int employees(LocalDate date,YearMonth month){
  var rows=sql.queryForList("""
   SELECT e.id employee_id,u.id user_id,u.email,u.display_name,COALESCE(t.status,'NO_TIMESHEET') state
   FROM employees e JOIN app_users u ON u.id=e.user_id LEFT JOIN timesheets t ON t.employee_id=e.id AND t.year=? AND t.month=?
   WHERE e.active AND (t.id IS NULL OR t.status IN ('DRAFT','REJECTED')) ORDER BY e.id
   """,month.getYear(),month.getMonthValue());
  int created=0;
  for(var row:rows){
   var recipient=new Recipient((UUID)row.get("user_id"),(String)row.get("email"),(String)row.get("display_name"));
   if(store.enqueue(NotificationType.TIMESHEET_REMINDER,recipient,"TIMESHEET_REMINDER:"+row.get("employee_id")+":"+month+":"+date,
    templates.timesheet(recipient.name(),month,(String)row.get("state")),month))created++;
  }
  return created;
 }
 private record Digest(UUID managerId,Recipient recipient,YearMonth month,long count){}
 private int managers(LocalDate date){
  // One query; bounded preview per manager/month. There is no per-timesheet email.
  var rows=sql.queryForList("""
   WITH pending AS (
    SELECT m.id manager_id,mu.id user_id,mu.email,mu.display_name manager_name,t.year,t.month,
     e.employee_number,u.display_name employee_name,
     count(*) OVER(PARTITION BY m.id,t.year,t.month) pending_count,
     row_number() OVER(PARTITION BY m.id,t.year,t.month ORDER BY e.employee_number,e.id) position
    FROM timesheets t JOIN employees e ON e.id=t.employee_id JOIN app_users u ON u.id=e.user_id
    JOIN employees m ON m.id=e.manager_id JOIN app_users mu ON mu.id=m.user_id
    WHERE t.status='PENDING_APPROVAL' AND m.active AND mu.role='MANAGER' AND e.id<>m.id
   ) SELECT * FROM pending WHERE position<=50 ORDER BY manager_id,year,month,position
   """);
  var bodies=new LinkedHashMap<Digest,StringBuilder>();
  for(var row:rows){
   var digest=new Digest((UUID)row.get("manager_id"),new Recipient((UUID)row.get("user_id"),(String)row.get("email"),(String)row.get("manager_name")),
    YearMonth.of((Integer)row.get("year"),(Integer)row.get("month")),((Number)row.get("pending_count")).longValue());
   bodies.computeIfAbsent(digest,k->new StringBuilder()).append(row.get("employee_number")).append(" — ").append(row.get("employee_name")).append("\n");
  }
  int created=0;
  for(var item:bodies.entrySet()){
   var d=item.getKey();if(store.enqueue(NotificationType.APPROVAL_REMINDER,d.recipient(),"APPROVAL_REMINDER:"+d.managerId()+":"+d.month()+":"+date,
    templates.manager(d.month(),d.count(),item.getValue().toString()),d.month()))created++;
  }
  return created;
 }
 private int finance(YearMonth month){
  var counts=sql.queryForMap("""
   SELECT count(*) FILTER(WHERE t.id IS NULL OR t.status<>'APPROVED') incomplete,
   count(*) FILTER(WHERE x.status='FAILED') failed,count(*) FILTER(WHERE x.status='UNKNOWN') unknown
   FROM employees e LEFT JOIN timesheets t ON t.employee_id=e.id AND t.year=? AND t.month=?
   LEFT JOIN sap_timesheet_exports x ON x.timesheet_id=t.id WHERE e.active
   """,month.getYear(),month.getMonthValue());
  long incomplete=((Number)counts.get("incomplete")).longValue(),failed=((Number)counts.get("failed")).longValue(),unknown=((Number)counts.get("unknown")).longValue();
  if(incomplete+failed+unknown==0)return 0;
  int created=0;var content=templates.finance(month,incomplete,failed,unknown);
  for(var user:store.financeRecipients())if(store.enqueue(NotificationType.FINANCE_MONTHLY_REMINDER,user,"FINANCE_MONTHLY_REMINDER:"+month+":"+user.id(),content,month))created++;
  return created;
 }
}
