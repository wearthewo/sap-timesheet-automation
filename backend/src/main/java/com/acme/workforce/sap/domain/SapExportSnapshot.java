package com.acme.workforce.sap.domain;
import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;
public record SapExportSnapshot(UUID exportId,String externalReference,Instant exportedAt,ApprovedTimesheetPayload payload) {
 public BigDecimal totalHours(){return payload.entries().stream().map(ApprovedTimesheetPayload.Entry::hours).reduce(BigDecimal.ZERO,BigDecimal::add);}
 public String filename(){
  String employee=payload.employeeNumber().replaceAll("[^A-Za-z0-9_-]","_");
  if(employee.length()>64)employee=employee.substring(0,64);
  return "sap-export-"+employee+"-%04d-%02d.xlsx".formatted(payload.year(),payload.month());
 }
}
