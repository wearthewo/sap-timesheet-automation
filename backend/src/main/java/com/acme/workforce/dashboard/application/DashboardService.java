package com.acme.workforce.dashboard.application;

import com.acme.workforce.organization.application.OrganizationAccess;
import com.acme.workforce.identity.domain.UserRole;
import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

/** Bounded read projections only. Every branch authorizes the current membership. */
@Service
public class DashboardService {
 private final JdbcTemplate sql;
 private final OrganizationAccess organization;
 private final Clock clock;
 private final ZoneId zone;
 public DashboardService(JdbcTemplate sql, OrganizationAccess organization,
   @Qualifier("notificationClock") Clock clock, @Value("${app.notifications.business-timezone}") String timezone) {
  this.sql=sql; this.organization=organization; this.clock=clock; this.zone=ZoneId.of(timezone);
 }
 public record Month(int year,int month) {}
 public record Administration(long activeEmployees,long activeProjects,long pendingInvitations) {}
 public record Finance(long awaitingExport,long queued,long processing,long succeeded,long failed,long unknown) {}
 public record Activity(UUID timesheetId,int year,int month,String decision,Instant decidedAt) {}
 public record Management(long pendingApprovals,List<Activity> recentDecisions) {}
 public record Summary(UserRole role,String businessDate,String timezone,Administration administration,
   Finance finance,Management management,List<Month> rejectedMonths) {}

 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Summary summary(UUID userId) {
  var role=organization.role(userId); var org=organization.id();
  var date=LocalDate.now(clock.withZone(zone));
  Administration admin=null; Finance finance=null; Management management=null;
  if(role==UserRole.ADMIN) {
   admin=new Administration(count("SELECT count(*) FROM employees WHERE organization_id=? AND active",org),
    count("SELECT count(*) FROM projects WHERE organization_id=? AND active",org),
    count("SELECT count(*) FROM organization_invitations WHERE organization_id=? AND status='PENDING' AND expires_at>?",org,java.sql.Timestamp.from(clock.instant())));
  }
  if(role==UserRole.ADMIN || role==UserRole.FINANCE) {
   finance=sql.queryForObject("""
    SELECT count(*) FILTER(WHERE x.id IS NULL), count(*) FILTER(WHERE x.status='QUEUED'),
     count(*) FILTER(WHERE x.status='PROCESSING'), count(*) FILTER(WHERE x.status='SUCCEEDED'),
     count(*) FILTER(WHERE x.status='FAILED'), count(*) FILTER(WHERE x.status='UNKNOWN')
    FROM timesheets t LEFT JOIN sap_timesheet_exports x ON x.timesheet_id=t.id AND x.organization_id=t.organization_id
    WHERE t.organization_id=? AND t.status='APPROVED'
    """,(r,n)->new Finance(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getLong(5),r.getLong(6)),org);
  }
  var employee=sql.query("SELECT id FROM employees WHERE organization_id=? AND user_id=? AND active",
    (r,n)->r.getObject(1,UUID.class),org,userId).stream().findFirst();
  List<Month> rejected=List.of();
  if(employee.isPresent()) {
   var id=employee.get();
   rejected=sql.query("SELECT year,month FROM timesheets WHERE organization_id=? AND employee_id=? AND status='REJECTED' ORDER BY year DESC,month DESC LIMIT 5",
    (r,n)->new Month(r.getInt(1),r.getInt(2)),org,id);
   if(role==UserRole.MANAGER) {
    long pending=count("""
     SELECT count(*) FROM timesheets t JOIN employees e ON e.id=t.employee_id AND e.organization_id=t.organization_id
     WHERE t.organization_id=? AND e.manager_id=? AND e.id<>? AND t.status='PENDING_APPROVAL'
     """,org,id,id);
    var decisions=sql.query("""
     SELECT t.id,t.year,t.month,a.decision,a.decided_at FROM timesheet_approvals a
     JOIN timesheets t ON t.id=a.timesheet_id AND t.organization_id=a.organization_id
     JOIN employees e ON e.id=t.employee_id AND e.organization_id=t.organization_id
     WHERE a.organization_id=? AND a.manager_employee_id=? AND e.manager_id=? AND e.id<>?
     ORDER BY a.decided_at DESC,a.id LIMIT 5
     """,(r,n)->new Activity(r.getObject(1,UUID.class),r.getInt(2),r.getInt(3),r.getString(4),r.getTimestamp(5).toInstant()),org,id,id,id);
    management=new Management(pending,decisions);
   }
  }
  return new Summary(role,date.toString(),zone.getId(),admin,finance,management,rejected);
 }
 private long count(String query,Object... args) { return sql.queryForObject(query,Long.class,args); }
}
