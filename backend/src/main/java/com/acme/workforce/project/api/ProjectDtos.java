package com.acme.workforce.project.api;

import com.acme.workforce.project.domain.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
import java.time.*;
public final class ProjectDtos {
 private ProjectDtos() {}
 public record WriteProject(@NotBlank @Size(max=50) String code,@NotBlank @Size(max=200) String name,@Size(max=2000) String description,@NotNull Boolean active) {}
 public record ProjectView(UUID id,String code,String name,String description,boolean active,Instant createdAt,Instant updatedAt) {
  public static ProjectView from(Project p) { return new ProjectView(p.getId(),p.getCode(),p.getName(),p.getDescription(),p.isActive(),p.getCreatedAt(),p.getUpdatedAt()); }
 }
 public record WriteAssignment(@NotNull UUID employeeId,@NotNull UUID projectId,@NotNull LocalDate validFrom,LocalDate validTo,@NotNull Boolean active) {}
 public record AssignmentView(UUID id,UUID employeeId,UUID projectId,LocalDate validFrom,LocalDate validTo,boolean active) {
  public static AssignmentView from(ProjectAssignment a) { return new AssignmentView(a.getId(),a.getEmployeeId(),a.getProjectId(),a.getValidFrom(),a.getValidTo(),a.isActive()); }
 }
}
