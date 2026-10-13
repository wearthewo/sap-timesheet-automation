package com.acme.workforce.organization.api;

import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.organization.application.OrganizationCreation;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/organizations")
public class OrganizationController {
 private final IdentityService identity; private final OrganizationCreation creation;
 public OrganizationController(IdentityService identity,OrganizationCreation creation) {this.identity=identity;this.creation=creation;}
 public record CreateRequest(@NotBlank @Size(max=200) String name,@NotBlank @Size(max=100) String slug) {
  @JsonAnySetter public void rejectUnexpected(String field,Object value) {throw new IllegalArgumentException("Unexpected organization field");}
 }
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public OrganizationCreation.Created create(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody CreateRequest request) {
  return creation.create(identity.current(jwt).getId(),jwt,key,request.name(),request.slug());
 }
}
