package com.acme.workforce.sap.infrastructure;
import com.acme.workforce.sap.api.SapDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
@Repository
public class SapJobQueries {
 private final JdbcTemplate sql;
 public SapJobQueries(JdbcTemplate sql){this.sql=sql;}
 private Instant time(ResultSet r,String name)throws SQLException {var t=r.getTimestamp(name);return t==null?null:t.toInstant();}
 public Map<UUID,JobView> jobs(Collection<UUID> ids){
  if(ids.isEmpty())return Map.of();
  var result=new HashMap<UUID,JobView>();
  new NamedParameterJdbcTemplate(sql).query("SELECT j.*,e.status AS export_status FROM sap_export_jobs j JOIN sap_timesheet_exports e ON e.id=j.export_id WHERE j.export_id IN (:ids)",Map.of("ids",ids),r->{
   String status=r.getString("status");
   result.put(r.getObject("export_id",UUID.class),new JobView(status,r.getString("operation"),r.getInt("attempt_count"),r.getInt("execution_count"),time(r,"last_attempt_at"),
    status.equals("PENDING")?time(r,"next_attempt_at"):null,
    status.equals("FAILED")&&r.getBoolean("retry_eligible")&&r.getString("export_status").equals("FAILED"),
    !status.equals("PROCESSING")&&r.getString("export_status").equals("UNKNOWN")));
  });
  return result;
 }
 public Page<AttemptView> attempts(UUID exportId,Pageable page){
  var list=sql.query("SELECT * FROM sap_export_attempts WHERE export_id=? ORDER BY attempt_number DESC LIMIT ? OFFSET ?",(r,n)->new AttemptView(r.getObject("id",UUID.class),r.getInt("attempt_number"),r.getString("operation"),time(r,"started_at"),time(r,"finished_at"),r.getString("outcome"),r.getString("safe_error_code"),r.getLong("duration_ms"),r.getString("external_reference")),exportId,page.getPageSize(),page.getOffset());
  return new PageImpl<>(list,page,sql.queryForObject("SELECT count(*) FROM sap_export_attempts WHERE export_id=?",Long.class,exportId));
 }
}
