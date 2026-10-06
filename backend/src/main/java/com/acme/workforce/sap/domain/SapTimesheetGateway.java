package com.acme.workforce.sap.domain;
public interface SapTimesheetGateway {
 SapExportResult export(ApprovedTimesheetPayload payload);
}
