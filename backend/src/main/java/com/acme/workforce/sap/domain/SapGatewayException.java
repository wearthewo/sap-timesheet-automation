package com.acme.workforce.sap.domain;
/** Only safe application messages cross the adapter boundary. */
public class SapGatewayException extends RuntimeException {
 public enum Failure {
  VALIDATION("SAP_VALIDATION_ERROR","SAP rejected the timesheet data.",false),
  AUTHENTICATION("SAP_AUTHENTICATION_FAILED","SAP authentication failed. Contact your administrator.",false),
  DUPLICATE("SAP_DUPLICATE","SAP reports an existing timesheet. Reconciliation is required.",true),
  UNAVAILABLE("SAP_UNAVAILABLE","SAP is unavailable. The outcome requires review before any further export.",true),
  TIMEOUT("SAP_TIMEOUT","SAP did not respond in time. It may have accepted the timesheet.",true),
  UNKNOWN("SAP_EXPORT_UNKNOWN","The SAP result could not be verified. Reconciliation is required.",true),
  CONFIGURATION("SAP_UNAVAILABLE","SAP integration is not configured. Contact your administrator.",false);
  public final String code,message; public final boolean ambiguous;
  Failure(String code,String message,boolean ambiguous){this.code=code;this.message=message;this.ambiguous=ambiguous;}
 }
 private final Failure failure;
 public SapGatewayException(Failure failure){super(failure.message);this.failure=failure;}
 public Failure failure(){return failure;}
}
