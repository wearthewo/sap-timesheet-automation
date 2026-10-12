package com.acme.workforce.reporting.infrastructure;
import com.acme.workforce.reporting.api.*;
import com.acme.workforce.reporting.api.ReportingDtos.*;
import com.acme.workforce.reporting.domain.*;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.data.domain.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
@Repository
public class ReportingQueries {
 private final com.acme.workforce.organization.application.OrganizationAccess organization;
 private final NamedParameterJdbcTemplate jdbc;
 public ReportingQueries(NamedParameterJdbcTemplate jdbc, com.acme.workforce.organization.application.OrganizationAccess organization) { this.organization=organization;this.jdbc=jdbc;}
 // One row per active employee. Latest decision uses the existing (timesheet,decided_at,id) index.
 private static final String BASE="""
 WITH base AS (
 SELECT e.id employee_id,e.employee_number,u.display_name employee_name,u.email,
 e.manager_id,mu.display_name manager_name,t.id timesheet_id,
 COALESCE(t.status,'NO_TIMESHEET') timesheet_state,COALESCE(t.total_hours,0.00) total_hours,t.submitted_at,
 a.decision,a.decided_at,
 CASE WHEN t.status IS DISTINCT FROM 'APPROVED' THEN 'NOT_APPLICABLE'
 ELSE COALESCE(x.status,'NOT_EXPORTED') END sap_state,x.external_reference,x.exported_at
 FROM employees e JOIN app_users u ON u.id=e.user_id
 LEFT JOIN employees m ON m.id=e.manager_id LEFT JOIN app_users mu ON mu.id=m.user_id
 LEFT JOIN timesheets t ON t.employee_id=e.id AND t.year=:year AND t.month=:month
 LEFT JOIN LATERAL (SELECT decision,decided_at FROM timesheet_approvals
 WHERE timesheet_id=t.id ORDER BY decided_at DESC,id DESC LIMIT 1) a ON true
 LEFT JOIN sap_timesheet_exports x ON x.timesheet_id=t.id
 WHERE e.active AND e.organization_id=:organization
 ), filtered AS (SELECT * FROM base b WHERE
 (:employee='' OR b.employee_number ILIKE :employeePattern ESCAPE '!' OR b.employee_name ILIKE :employeePattern ESCAPE '!' OR b.email ILIKE :employeePattern ESCAPE '!')
 AND (:state='' OR b.timesheet_state=:state) AND (:sap='' OR b.sap_state=:sap)
 AND (:project='' OR EXISTS(SELECT 1 FROM timesheet_entries te JOIN projects p ON p.id=te.project_id
 WHERE te.timesheet_id=b.timesheet_id AND (p.code ILIKE :projectPattern ESCAPE '!' OR p.name ILIKE :projectPattern ESCAPE '!')))
 )
 """;
 private static final String DETAILS="""
 , detail AS (SELECT b.*,te.id entry_id,te.project_id,p.code project_code,p.name project_name,te.work_date,te.hours,te.description
 FROM filtered b JOIN timesheet_entries te ON te.timesheet_id=b.timesheet_id JOIN projects p ON p.id=te.project_id
 WHERE b.timesheet_state='APPROVED' AND (:mode='APPROVED' OR b.sap_state='SUCCEEDED')
 AND (:project='' OR p.code ILIKE :projectPattern ESCAPE '!' OR p.name ILIKE :projectPattern ESCAPE '!'))
 """;
 private MapSqlParameterSource params(ReportFilter f){
  String employee=f.employee()==null?"":f.employee().strip(),project=f.project()==null?"":f.project().strip();
  return new MapSqlParameterSource().addValue("organization",organization.id()).addValue("year",f.year()).addValue("month",f.month())
   .addValue("employee",employee).addValue("employeePattern",pattern(employee))
   .addValue("project",project).addValue("projectPattern",pattern(project))
   .addValue("state",f.timesheetStatus()==null?"":f.timesheetStatus().name())
   .addValue("sap",f.sapStatus()==null?"":f.sapStatus().name()).addValue("mode",f.effectiveMode().name());
 }
 private String pattern(String value){return "%"+value.replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
 private static Instant instant(ResultSet r,String column)throws SQLException{var value=r.getTimestamp(column);return value==null?null:value.toInstant();}
 private static UUID uuid(ResultSet r,String column)throws SQLException{return r.getObject(column,UUID.class);}
 public MonthlyFinanceSummary summary(ReportFilter f){
  return jdbc.queryForObject(BASE+"""
   SELECT count(*) employees,
   count(*) FILTER(WHERE timesheet_state='NO_TIMESHEET') missing,
   count(*) FILTER(WHERE timesheet_state='DRAFT') draft,
   count(*) FILTER(WHERE timesheet_state='PENDING_APPROVAL') pending,
   count(*) FILTER(WHERE timesheet_state='REJECTED') rejected,
   count(*) FILTER(WHERE timesheet_state='APPROVED') approved,
   count(*) FILTER(WHERE sap_state='NOT_EXPORTED') not_exported,
   count(*) FILTER(WHERE sap_state='QUEUED') queued,count(*) FILTER(WHERE sap_state='PROCESSING') processing,
   count(*) FILTER(WHERE sap_state='SUCCEEDED') succeeded,count(*) FILTER(WHERE sap_state='FAILED') failed,
   count(*) FILTER(WHERE sap_state='UNKNOWN') unknown,
   COALESCE(sum(total_hours),0.00) recorded_hours,
   COALESCE(sum(total_hours) FILTER(WHERE timesheet_state='APPROVED'),0.00) approved_hours,
   COALESCE(sum(total_hours) FILTER(WHERE timesheet_state='APPROVED' AND sap_state='SUCCEEDED'),0.00) exported_hours
   FROM filtered
   """,params(f),(r,n)->new MonthlyFinanceSummary(f.year(),f.month(),r.getLong("employees"),r.getLong("missing"),r.getLong("draft"),
    r.getLong("pending"),r.getLong("rejected"),r.getLong("approved"),r.getLong("not_exported"),r.getLong("queued"),
    r.getLong("processing"),r.getLong("succeeded"),r.getLong("failed"),r.getLong("unknown"),
    r.getBigDecimal("recorded_hours"),r.getBigDecimal("approved_hours"),r.getBigDecimal("exported_hours")));
 }
 private final RowMapper<EmployeeMonthlyReportRow> employeeMapper=(r,n)->new EmployeeMonthlyReportRow(uuid(r,"employee_id"),
  r.getString("employee_number"),r.getString("employee_name"),r.getString("email"),uuid(r,"manager_id"),r.getString("manager_name"),
  uuid(r,"timesheet_id"),ReportingTimesheetState.valueOf(r.getString("timesheet_state")),r.getBigDecimal("total_hours"),instant(r,"submitted_at"),
  r.getString("decision"),instant(r,"decided_at"),ReportingSapState.valueOf(r.getString("sap_state")),r.getString("external_reference"),instant(r,"exported_at"));
 private static final String PROJECTS="SELECT project_id,project_code,project_name,sum(hours) total_hours,count(DISTINCT employee_id) employees FROM detail GROUP BY project_id,project_code,project_name";
 private static final String BREAKDOWN="SELECT employee_id,employee_number,employee_name,project_id,project_code,project_name,sum(hours) total_hours FROM detail GROUP BY employee_id,employee_number,employee_name,project_id,project_code,project_name";
 private final RowMapper<ProjectMonthlyTotal> projectMapper=(r,n)->new ProjectMonthlyTotal(uuid(r,"project_id"),r.getString("project_code"),r.getString("project_name"),r.getBigDecimal("total_hours"),r.getLong("employees"));
 private final RowMapper<EmployeeProjectHours> breakdownMapper=(r,n)->new EmployeeProjectHours(uuid(r,"employee_id"),r.getString("employee_number"),r.getString("employee_name"),
  uuid(r,"project_id"),r.getString("project_code"),r.getString("project_name"),r.getBigDecimal("total_hours"));
 private <T> Page<T> page(String cte,String select,String order,ReportFilter f,Pageable page,RowMapper<T> mapper){
  var p=params(f).addValue("limit",page.getPageSize()).addValue("offset",page.getOffset());
  long count=jdbc.queryForObject(cte+" SELECT count(*) FROM ("+select+") counted",p,Long.class);
  return new PageImpl<>(jdbc.query(cte+select+order+" LIMIT :limit OFFSET :offset",p,mapper),page,count);
 }
 public Page<EmployeeMonthlyReportRow> employees(ReportFilter f,Pageable page){return page(BASE,"SELECT * FROM filtered"," ORDER BY employee_number,employee_id",f,page,employeeMapper);}
 public Page<ProjectMonthlyTotal> projects(ReportFilter f,Pageable page){return page(BASE+DETAILS,PROJECTS," ORDER BY project_code,project_id",f,page,projectMapper);}
 public Page<EmployeeProjectHours> employeeProjects(ReportFilter f,Pageable page){return page(BASE+DETAILS,BREAKDOWN," ORDER BY employee_number,employee_id,project_code,project_id",f,page,breakdownMapper);}
 public List<ApprovedHourDetail> details(ReportFilter f,int limit){
  return jdbc.query(BASE+DETAILS+"SELECT * FROM detail ORDER BY employee_number,employee_id,work_date,project_code,entry_id LIMIT :limit",
   params(f).addValue("limit",limit),(r,n)->new ApprovedHourDetail(r.getString("employee_number"),r.getString("employee_name"),r.getString("email"),
    r.getString("manager_name"),f.year(),f.month(),r.getString("project_code"),r.getString("project_name"),r.getDate("work_date").toLocalDate(),
    r.getBigDecimal("hours"),r.getString("description"),r.getString("timesheet_state"),instant(r,"submitted_at"),r.getString("decision"),
    instant(r,"decided_at"),ReportingSapState.valueOf(r.getString("sap_state")),r.getString("external_reference")));
 }
}
