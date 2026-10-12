package com.acme.workforce.notification.application;
import com.acme.workforce.notification.api.NotificationDtos.View;
import com.acme.workforce.notification.domain.*;
import com.acme.workforce.common.application.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.*;
import java.util.UUID;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class NotificationHistory {
 private final com.acme.workforce.organization.application.OrganizationAccess organization;
 private final JdbcTemplate sql;
 public NotificationHistory(JdbcTemplate sql, com.acme.workforce.organization.application.OrganizationAccess organization) { this.organization=organization;this.sql=sql;}
 private final org.springframework.jdbc.core.RowMapper<View> mapper=(r,n)->new View(r.getObject("id",UUID.class),NotificationType.valueOf(r.getString("type")),r.getString("subject"),r.getString("body"),
  NotificationStatus.valueOf(r.getString("status")),r.getTimestamp("created_at").toInstant(),r.getTimestamp("sent_at")==null?null:r.getTimestamp("sent_at").toInstant());
 public Page<View> list(UUID user,Pageable page){
  long count=sql.queryForObject("SELECT count(*) FROM notifications WHERE organization_id=? AND recipient_user_id=?",Long.class,organization.id(),user);
  var rows=sql.query("SELECT * FROM notifications WHERE organization_id=? AND recipient_user_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",mapper,organization.id(),user,page.getPageSize(),page.getOffset());
  return new PageImpl<>(rows,page,count);
 }
 public View detail(UUID user,UUID id){
  return sql.query("SELECT * FROM notifications WHERE organization_id=? AND id=? AND recipient_user_id=?",mapper,organization.id(),id,user).stream().findFirst()
   .orElseThrow(()->new DomainException(404,"NOTIFICATION_NOT_FOUND","Notification not found."));
 }
}
