package com.acme.workforce.approval.api;
import com.acme.workforce.approval.domain.ApprovalDecision;
import com.acme.workforce.timesheet.api.TimesheetDtos;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
public final class ApprovalDtos {
 private ApprovalDtos() {}
 public record DecisionRequest(@Size(max=2000) String comment) {}
 public record DecisionView(UUID id, UUID managerEmployeeId, ApprovalDecision decision, String comment, Instant decidedAt) {}
 public record EmployeeView(UUID id, String employeeNumber, String displayName) {}
 public record PendingItem(EmployeeView employee, TimesheetDtos.Summary timesheet) {}
 public record Review(EmployeeView employee, TimesheetDtos.Detail timesheet) {}
}
