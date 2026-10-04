package com.acme.workforce.identity.api;

import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.identity.domain.*;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/auth")
public class IdentityController {
 private final IdentityService identity;
 public IdentityController(IdentityService identity) { this.identity=identity; }
 public record Me(UUID id,String email,String displayName,UserRole role) {}
 @GetMapping("/me")
 public Me me(@AuthenticationPrincipal Jwt jwt) {
  var user=identity.current(jwt); return new Me(user.getId(),user.getEmail(),user.getDisplayName(),user.getRole());
 }
}
