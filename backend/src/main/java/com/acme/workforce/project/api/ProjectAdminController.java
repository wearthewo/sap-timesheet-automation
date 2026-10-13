package com.acme.workforce.project.api;
import com.acme.workforce.project.application.ProjectService;
import com.acme.workforce.project.application.ProjectService.AdminAssignment;
import com.acme.workforce.project.api.ProjectDtos.ProjectView;
import com.acme.workforce.common.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;
@RestController @Validated @RequestMapping("/api/v1/admin") @PreAuthorize("@applicationAuthorization.isAdmin(authentication)")
public class ProjectAdminController {
 private final ProjectService projects;public ProjectAdminController(ProjectService projects){this.projects=projects;}
 public record State(@NotNull Boolean active){}
 @GetMapping("/projects") public PageResponse<ProjectView> list(@RequestParam(defaultValue="") @Size(max=200) String search,@RequestParam(required=false) Boolean active,@RequestParam(defaultValue="code") String sort,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return PageResponse.from(projects.adminList(search,active,sort,PageRequest.of(page,size)));}
 @PostMapping("/projects/{id}/status") public ProjectView state(@PathVariable UUID id,@Valid @RequestBody State r){return projects.state(id,r.active());}
 @GetMapping("/employees/{id}/assignments") public PageResponse<AdminAssignment> employee(@PathVariable UUID id,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return PageResponse.from(projects.adminAssignments(id,null,PageRequest.of(page,size)));}
 @GetMapping("/projects/{id}/assignments") public PageResponse<AdminAssignment> project(@PathVariable UUID id,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return PageResponse.from(projects.adminAssignments(null,id,PageRequest.of(page,size)));}
}
