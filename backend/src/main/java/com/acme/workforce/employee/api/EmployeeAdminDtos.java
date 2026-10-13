package com.acme.workforce.employee.api;
import jakarta.validation.constraints.*;
import java.util.UUID;
public final class EmployeeAdminDtos {
 private EmployeeAdminDtos(){}
 public record Write(@NotBlank @Size(max=100) String employeeNumber,@NotBlank @Size(max=255) String displayName,UUID managerId,@NotNull Boolean active,UUID userId){}
 public record Edit(@NotBlank @Size(max=100) String employeeNumber,@NotBlank @Size(max=255) String displayName,UUID managerId,@NotNull @Min(0) Long version){}
 public record State(@NotNull Boolean active,@NotNull @Min(0) Long version){}
 public record Link(@NotNull UUID userId,@NotNull @Min(0) Long version){}
 public record View(UUID id,String employeeNumber,String displayName,boolean active,UUID managerId,String managerName,UUID userId,String email,long version,String invitationStatus){}
}
