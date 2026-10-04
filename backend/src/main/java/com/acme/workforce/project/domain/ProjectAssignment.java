package com.acme.workforce.project.domain;

import com.acme.workforce.common.domain.TimestampedEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="project_assignments")
public class ProjectAssignment extends TimestampedEntity {
 @Column(nullable=false) private UUID employeeId;
 @Column(nullable=false) private UUID projectId;
 @Column(nullable=false) private LocalDate validFrom;
 private LocalDate validTo;
 @Column(nullable=false) private boolean active;
 protected ProjectAssignment() {}
 public ProjectAssignment(UUID employeeId,UUID projectId,LocalDate validFrom,LocalDate validTo,boolean active) {
  this.employeeId=employeeId; this.projectId=projectId; this.validFrom=validFrom; this.validTo=validTo; this.active=active;
 }
 public UUID getEmployeeId() { return employeeId; }
 public UUID getProjectId() { return projectId; }
 public LocalDate getValidFrom() { return validFrom; }
 public LocalDate getValidTo() { return validTo; }
 public boolean isActive() { return active; }
}
