package com.acme.workforce.project.api;

import com.acme.workforce.project.application.ProjectService;
import com.acme.workforce.project.api.ProjectDtos.*;
import com.acme.workforce.common.api.PageResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.util.UUID;
@RestController @Validated @RequestMapping("/api/v1/projects")
public class ProjectController {
 private final ProjectService projects;
 public ProjectController(ProjectService projects) { this.projects=projects; }
 @GetMapping
 public PageResponse<ProjectView> list(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return PageResponse.from(projects.list(PageRequest.of(page,size,Sort.by("code").and(Sort.by("id"))))); }
 @GetMapping("/{id}") public ProjectView get(@PathVariable UUID id) { return projects.get(id); }
 @PostMapping @PreAuthorize("@applicationAuthorization.isAdmin(authentication)")
 public ResponseEntity<ProjectView> create(@Valid @RequestBody WriteProject request) {
  var p=projects.create(request); return ResponseEntity.created(URI.create("/api/v1/projects/"+p.id())).body(p);
 }
 @PutMapping("/{id}") @PreAuthorize("@applicationAuthorization.isAdmin(authentication)")
 public ProjectView update(@PathVariable UUID id,@Valid @RequestBody WriteProject request) { return projects.update(id,request); }
}
