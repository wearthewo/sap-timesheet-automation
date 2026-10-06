package com.acme.workforce.approval.domain;

import com.acme.workforce.common.domain.TimestampedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;
import java.util.UUID;

@Entity @Immutable @Table(name="timesheet_approvals")
public class TimesheetApproval extends TimestampedEntity {
 @Column(nullable=false,updatable=false) private UUID timesheetId;
 @Column(nullable=false,updatable=false) private UUID managerEmployeeId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=20) private ApprovalDecision decision;
 @Column(updatable=false,length=2000) private String comment;
 @Column(nullable=false,updatable=false) private Instant decidedAt;
 protected TimesheetApproval() {}
 public TimesheetApproval(UUID timesheetId, UUID managerEmployeeId, ApprovalDecision decision, String comment) {
  this.timesheetId=timesheetId; this.managerEmployeeId=managerEmployeeId;
  this.decision=decision; this.comment=comment; this.decidedAt=Instant.now();
 }
 public UUID getTimesheetId() { return timesheetId; }
 public UUID getManagerEmployeeId() { return managerEmployeeId; }
 public ApprovalDecision getDecision() { return decision; }
 public String getComment() { return comment; }
 public Instant getDecidedAt() { return decidedAt; }
}
