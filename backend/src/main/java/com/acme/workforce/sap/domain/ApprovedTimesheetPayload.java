package com.acme.workforce.sap.domain;
import java.util.*;
import java.time.LocalDate;
import java.math.BigDecimal;
public record ApprovedTimesheetPayload(UUID timesheetId, String employeeNumber, int year, int month, List<Entry> entries) {
 public ApprovedTimesheetPayload { entries=List.copyOf(entries); }
 public String idempotencyKey() { return "SAP_TIMESHEET:"+timesheetId; }
 public record Entry(String projectCode, LocalDate workDate, BigDecimal hours, String description) {}
}
