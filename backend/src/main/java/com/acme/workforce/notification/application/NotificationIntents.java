package com.acme.workforce.notification.application;
import com.acme.workforce.notification.domain.NotificationType;
import com.acme.workforce.notification.infrastructure.NotificationStore;
import com.acme.workforce.notification.infrastructure.NotificationStore.Recipient;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.UUID;
@Service
public class NotificationIntents {
 private final JdbcTemplate sql;private final NotificationStore store;private final NotificationTemplates templates;
 public NotificationIntents(JdbcTemplate sql,NotificationStore store,NotificationTemplates templates){this.sql=sql;this.store=store;this.templates=templates;}
 @Transactional(propagation=Propagation.MANDATORY)
 public void approval(UUID decisionId){
  sql.query("""
   SELECT a.decision,a.comment,a.decided_at,t.year,t.month,u.id,u.email,u.display_name,mu.display_name manager_name FROM timesheet_approvals a
   JOIN timesheets t ON t.id=a.timesheet_id JOIN employees e ON e.id=t.employee_id JOIN app_users u ON u.id=e.user_id LEFT JOIN employees m ON m.id=a.manager_employee_id LEFT JOIN app_users mu ON mu.id=m.user_id WHERE a.id=?
   """,rs->{
    boolean approved=rs.getString("decision").equals("APPROVED");
    var type=approved?NotificationType.TIMESHEET_APPROVED:NotificationType.TIMESHEET_REJECTED;
    var user=new Recipient(rs.getObject("id",UUID.class),rs.getString("email"),rs.getString("display_name"));
    store.enqueue(type,user,type+":"+decisionId+":"+user.id(),templates.decision(user.name(),YearMonth.of(rs.getInt("year"),rs.getInt("month")),approved,rs.getString("comment"),rs.getTimestamp("decided_at").toInstant(),rs.getString("manager_name")));
   },decisionId);
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public void sap(UUID exportId,int execution,String status){
  if(!status.equals("FAILED")&&!status.equals("UNKNOWN"))return;
  var row=sql.queryForMap("""
   SELECT t.id,t.year,t.month,e.employee_number,x.failure_code FROM sap_timesheet_exports x
   JOIN timesheets t ON t.id=x.timesheet_id JOIN employees e ON e.id=t.employee_id WHERE x.id=?
   """,exportId);
  var type=status.equals("FAILED")?NotificationType.SAP_EXPORT_FAILED:NotificationType.SAP_EXPORT_UNKNOWN;
  // One UNKNOWN alert per logical export, not one email for each automatic reconciliation/retry.
  String event=type+":"+exportId+(status.equals("FAILED")?":"+execution:"");
  var content=templates.sap((String)row.get("employee_number"),YearMonth.of((Integer)row.get("year"),(Integer)row.get("month")),status,(String)row.get("failure_code"),(UUID)row.get("id"));
  for(var user:store.financeRecipients())store.enqueue(type,user,event+":"+user.id(),content);
 }
}
