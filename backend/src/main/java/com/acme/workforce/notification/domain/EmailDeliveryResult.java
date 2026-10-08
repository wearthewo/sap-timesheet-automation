package com.acme.workforce.notification.domain;
// Accepted by SMTP; this is not evidence that a person read the email.
public record EmailDeliveryResult(boolean accepted) {}
