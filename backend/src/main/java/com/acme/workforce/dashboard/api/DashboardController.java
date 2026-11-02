package com.acme.workforce.dashboard.api;

import com.acme.workforce.dashboard.application.DashboardService;
import com.acme.workforce.identity.application.IdentityService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
 private final DashboardService dashboard;
 private final IdentityService identity;
 public DashboardController(DashboardService dashboard, IdentityService identity) { this.dashboard=dashboard; this.identity=identity; }
 @GetMapping public DashboardService.Summary summary(@AuthenticationPrincipal Jwt jwt) {
  return dashboard.summary(identity.current(jwt).getId());
 }
}
