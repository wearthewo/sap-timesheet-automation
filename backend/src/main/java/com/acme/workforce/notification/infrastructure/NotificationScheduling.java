package com.acme.workforce.notification.infrastructure;
import com.acme.workforce.notification.application.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
@Configuration @EnableScheduling
public class NotificationScheduling {
 @Configuration @ConditionalOnProperty(name="app.notifications.scheduler-enabled",havingValue="true")
 static class EvaluationSchedule {
  private final NotificationEvaluation evaluation;
  EvaluationSchedule(NotificationEvaluation evaluation){this.evaluation=evaluation;}
  @Scheduled(fixedDelayString="${app.notifications.evaluation-interval}",initialDelayString="${app.notifications.evaluation-interval}")
  void evaluate(){evaluation.evaluate();}
 }
 @Configuration @ConditionalOnProperty(name="app.notifications.worker-enabled",havingValue="true")
 static class DeliverySchedule {
  private final NotificationWorker worker;
  DeliverySchedule(NotificationWorker worker,NotificationPolicy policy,
   @Value("${spring.mail.properties.mail.smtp.connectiontimeout}") long connect,
   @Value("${spring.mail.properties.mail.smtp.timeout}") long read,
   @Value("${spring.mail.properties.mail.smtp.writetimeout}") long write){
   if(connect<=0||read<=0||write<=0||policy.lease().compareTo(Duration.ofMillis((connect+read+write)*10).plusSeconds(10))<=0)
    throw new IllegalArgumentException("Notification lease must exceed conservative SMTP timeout allowance");
   this.worker=worker;
  }
  @Scheduled(fixedDelayString="${app.notifications.poll-interval}",initialDelayString="${app.notifications.poll-interval}")
  void deliver(){worker.runOnce();}
 }
}
