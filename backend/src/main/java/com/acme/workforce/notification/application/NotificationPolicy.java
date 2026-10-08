package com.acme.workforce.notification.application;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.net.URI;
@Component
public record NotificationPolicy(int batchSize,int maxAttempts,Duration initialBackoff,Duration maxBackoff,Duration lease,
 ZoneId zone,int employeeDay,int financeDay,String publicBaseUrl) {
 @org.springframework.beans.factory.annotation.Autowired
 public NotificationPolicy(@Value("${app.notifications.batch-size}") int batchSize,@Value("${app.notifications.max-attempts}") int maxAttempts,
  @Value("${app.notifications.initial-backoff}") Duration initialBackoff,@Value("${app.notifications.max-backoff}") Duration maxBackoff,
  @Value("${app.notifications.processing-timeout}") Duration lease,@Value("${app.notifications.business-timezone}") String timezone,
  @Value("${app.notifications.timesheet-reminder-day}") int employeeDay,@Value("${app.notifications.finance-reminder-day}") int financeDay,
  @Value("${app.notifications.public-base-url}") String publicBaseUrl){
  this(batchSize,maxAttempts,initialBackoff,maxBackoff,lease,ZoneId.of(timezone),employeeDay,financeDay,publicBaseUrl.replaceAll("/+$",""));
 }
 public NotificationPolicy {
  if(batchSize<1||batchSize>100||maxAttempts<1||maxAttempts>100||employeeDay<1||employeeDay>31||financeDay<1||financeDay>31)throw new IllegalArgumentException("Invalid notification limits");
  for(var d:new Duration[]{initialBackoff,maxBackoff,lease})if(d.isZero()||d.isNegative())throw new IllegalArgumentException("Notification durations must be positive");
  if(maxBackoff.compareTo(initialBackoff)<0)throw new IllegalArgumentException("Notification maximum backoff must cover initial delay");
  var uri=URI.create(publicBaseUrl);if(!java.util.Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Invalid public application URL");
 }
 public Duration backoff(int attempts){
  var d=initialBackoff;
  for(int i=1;i<attempts && d.compareTo(maxBackoff)<0;i++){if(d.compareTo(maxBackoff.dividedBy(2))>0)return maxBackoff;d=d.multipliedBy(2);}
  return d.compareTo(maxBackoff)>0?maxBackoff:d;
 }
 public boolean due(LocalDate date,int day){return date.getDayOfMonth()>=Math.min(day,date.lengthOfMonth());}
}
