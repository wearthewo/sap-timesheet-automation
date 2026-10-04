package com.acme.workforce.employee.api;

import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.project.application.ProjectService;
import com.acme.workforce.project.api.ProjectDtos.ProjectView;
import com.acme.workforce.common.api.PageResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
@RestController @Validated @RequestMapping("/api/v1/employees")
public class EmployeeController {
 private final IdentityService identity; private final ProjectService projects;
 public EmployeeController(IdentityService identity,ProjectService projects) { this.identity=identity; this.projects=projects; }
 @GetMapping("/me/projects")
 public PageResponse<ProjectView> assigned(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
  return PageResponse.from(projects.assigned(identity.current(jwt).getId(),PageRequest.of(page,size,Sort.by("code").and(Sort.by("id")))));
 }
}
