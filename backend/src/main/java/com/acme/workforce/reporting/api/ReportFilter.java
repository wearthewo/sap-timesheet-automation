package com.acme.workforce.reporting.api;
import com.acme.workforce.reporting.domain.*;
import jakarta.validation.constraints.*;
public record ReportFilter(
 @NotNull @Min(1) @Max(9999) Integer year, @NotNull @Min(1) @Max(12) Integer month,
 @Size(max=100) String employee, @Size(max=100) String project,
 ReportingTimesheetState timesheetStatus, ReportingSapState sapStatus, ReportMode mode) {
 public ReportMode effectiveMode(){return mode==null?ReportMode.APPROVED:mode;}
}
