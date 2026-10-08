package com.acme.workforce.notification.infrastructure;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import java.time.Clock;
@Configuration
public class NotificationClockConfiguration {
 @Bean("notificationClock") @ConditionalOnMissingBean(name="notificationClock")
 public Clock notificationClock(){return Clock.systemUTC();}
}
