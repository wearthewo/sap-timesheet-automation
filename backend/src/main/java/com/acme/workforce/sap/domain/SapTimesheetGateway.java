package com.acme.workforce.sap.domain;
import java.util.Optional;
public interface SapTimesheetGateway {
 SapExportResult export(ApprovedTimesheetPayload payload);
 Optional<SapExportResult> findByExportIdentity(String exportIdentity);
}
