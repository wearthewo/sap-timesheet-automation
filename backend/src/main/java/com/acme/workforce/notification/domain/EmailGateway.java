package com.acme.workforce.notification.domain;
public interface EmailGateway { EmailDeliveryResult send(EmailMessage message); }
