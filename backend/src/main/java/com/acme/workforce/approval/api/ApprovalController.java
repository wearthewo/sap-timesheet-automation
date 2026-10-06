package com.acme.workforce.approval.api;
import com.acme.workforce.approval.api.ApprovalDtos.*;
import com.acme.workforce.approval.application.ApprovalService;
import com.acme.workforce.approval.domain.ApprovalDecision;
import com.acme.workforce.common.api.PageResponse;
import com.acme.workforce.identity.application.IdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @Validated @RequestMapping("/api/v1/approvals")
public class ApprovalController {
 private final ApprovalService approvals; private final IdentityService identity;
 public ApprovalController(ApprovalService approvals,IdentityService identity) {this.approvals=approvals;this.identity=identity;}
 @GetMapping("/pending")
 public PageResponse<PendingItem> pending(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="12") @Min(1) @Max(100) int size) {
  return PageResponse.from(approvals.pending(identity.current(jwt).getId(),PageRequest.of(page,size,Sort.by("submittedAt","id"))));
 }
 @GetMapping("/timesheets/{id}")
 public Review review(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) { return approvals.review(identity.current(jwt).getId(),id); }
 @PostMapping("/timesheets/{id}/approve")
 public Review approve(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestHeader("X-Timesheet-Version") @Min(0) long version,@Valid @RequestBody(required=false) DecisionRequest request) {
  return approvals.decide(identity.current(jwt).getId(),id,version,ApprovalDecision.APPROVED,request==null?null:request.comment());
 }
 @PostMapping("/timesheets/{id}/reject")
 public Review reject(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestHeader("X-Timesheet-Version") @Min(0) long version,@Valid @RequestBody(required=false) DecisionRequest request) {
  return approvals.decide(identity.current(jwt).getId(),id,version,ApprovalDecision.REJECTED,request==null?null:request.comment());
 }
}
