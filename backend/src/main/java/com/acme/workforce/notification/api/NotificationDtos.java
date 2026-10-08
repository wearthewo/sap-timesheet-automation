package com.acme.workforce.notification.api;
import com.acme.workforce.notification.domain.*;
import java.time.Instant;
import java.util.UUID;
public final class NotificationDtos {
 private NotificationDtos(){}
 public record View(UUID id,NotificationType type,String subject,String body,NotificationStatus status,Instant createdAt,Instant sentAt){}
}
