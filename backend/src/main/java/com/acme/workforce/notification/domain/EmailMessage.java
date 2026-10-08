package com.acme.workforce.notification.domain;
import java.util.UUID;
public record EmailMessage(UUID notificationId,String recipient,String subject,String body) {}
