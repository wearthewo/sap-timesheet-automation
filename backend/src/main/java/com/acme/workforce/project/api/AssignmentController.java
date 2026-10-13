package com.acme.workforce.project.api;

import com.acme.workforce.project.application.ProjectService;
import com.acme.workforce.project.api.ProjectDtos.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
@RestController @RequestMapping("/api/v1/project-assignments")
public class AssignmentController {
 private final ProjectService projects;
 public AssignmentController(ProjectService projects) { this.projects=projects; }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@applicationAuthorization.isAdmin(authentication)")
 public AssignmentView create(@Valid @RequestBody WriteAssignment request) { return projects.assign(request); }
 @PutMapping("/{id}") @PreAuthorize("@applicationAuthorization.isAdmin(authentication)")
 public AssignmentView update(@PathVariable java.util.UUID id,@Valid @RequestBody WriteAssignment request){return projects.updateAssignment(id,request);}
}
