package com.acme.workforce.notification.domain;
public class EmailDeliveryException extends RuntimeException {
 public enum Failure {
  CONNECTION("EMAIL_CONNECTION_FAILED","The mail server could not be reached.",true),
  TIMEOUT("EMAIL_TIMEOUT","SMTP acceptance could not be confirmed.",true),
  TEMPORARY("EMAIL_TEMPORARY_FAILURE","The mail server temporarily rejected delivery.",true),
  AUTHENTICATION("EMAIL_AUTHENTICATION_FAILED","Mail server authentication requires administrator review.",false),
  RECIPIENT("EMAIL_RECIPIENT_REJECTED","The recipient address was rejected.",false),
  CONTENT("EMAIL_INVALID_CONTENT","The email could not be prepared safely.",false),
  PERMANENT("EMAIL_PERMANENT_FAILURE","The mail server rejected delivery.",false),
  UNKNOWN("EMAIL_DELIVERY_UNCONFIRMED","SMTP acceptance could not be confirmed.",true);
  public final String code,message;public final boolean retryable;
  Failure(String code,String message,boolean retryable){this.code=code;this.message=message;this.retryable=retryable;}
 }
 private final Failure failure;
 public EmailDeliveryException(Failure failure){super(failure.message);this.failure=failure;}
 public Failure failure(){return failure;}
}
