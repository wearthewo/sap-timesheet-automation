package com.acme.workforce.organization.api;
import com.acme.workforce.organization.application.Invitations;
import com.acme.workforce.organization.application.Invitations.*;
import com.acme.workforce.common.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;
@RestController @Validated @RequestMapping("/api/v1")
public class InvitationController {
 private final Invitations invitations;public InvitationController(Invitations invitations){this.invitations=invitations;}
 @GetMapping("/admin/invitations") @PreAuthorize("@applicationAuthorization.isAdmin(authentication)") public PageResponse<View> list(@RequestParam(defaultValue="") @Pattern(regexp="|PENDING|ACCEPTED|REVOKED|EXPIRED") String status,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return PageResponse.from(invitations.list(status,PageRequest.of(page,size)));}
 @PostMapping("/admin/invitations") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @PreAuthorize("@applicationAuthorization.isAdmin(authentication)") public View create(@Valid @RequestBody Create r){return invitations.create(r);}
 @PostMapping("/admin/invitations/{id}/revoke") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) @PreAuthorize("@applicationAuthorization.isAdmin(authentication)") public void revoke(@PathVariable UUID id){invitations.revoke(id);}
 @PostMapping("/invitations/preview") public Preview preview(@Valid @RequestBody Token r,@AuthenticationPrincipal Jwt jwt){return invitations.preview(r.token(),jwt);}
 @PostMapping("/invitations/accept") public Preview accept(@Valid @RequestBody Token r,@AuthenticationPrincipal Jwt jwt){return invitations.accept(r.token(),jwt);}
}
