package com.acme.workforce.sap.api;
import com.acme.workforce.sap.api.SapDtos.*;
import com.acme.workforce.sap.application.*;
import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.common.api.PageResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
@RestController @Validated @RequestMapping("/api/v1/sap/timesheets")
public class SapController {
 private final SapExportService service;private final SapExportTransactions queries;private final IdentityService identity;
 public SapController(SapExportService service,SapExportTransactions queries,IdentityService identity){this.service=service;this.queries=queries;this.identity=identity;}
 @GetMapping public PageResponse<WorkItem> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
  return PageResponse.from(queries.list(identity.current(jwt).getId(),PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"year","month","id"))));
 }
 @GetMapping("/{id}") public ExportDetail detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return queries.detail(identity.current(jwt).getId(),id);}
 @GetMapping("/{id}/export") public ExportView state(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return queries.state(identity.current(jwt).getId(),id);}
 @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED) @PostMapping("/{id}/export") public ExportView export(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestHeader("X-Timesheet-Version") @Min(0) long version){return service.export(identity.current(jwt).getId(),id,version);}
 @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED) @PostMapping("/{id}/export/retry")
 public ExportView retry(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.retry(identity.current(jwt).getId(),id);}
 @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED) @PostMapping("/{id}/export/reconcile")
 public ExportView reconcile(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.reconcile(identity.current(jwt).getId(),id);}
 @GetMapping("/{id}/export/attempts")
 public PageResponse<AttemptView> attempts(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return PageResponse.from(queries.attempts(identity.current(jwt).getId(),id,PageRequest.of(page,size)));}
}