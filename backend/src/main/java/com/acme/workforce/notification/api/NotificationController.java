package com.acme.workforce.notification.api;
import com.acme.workforce.notification.application.NotificationHistory;
import com.acme.workforce.notification.api.NotificationDtos.View;
import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.common.api.PageResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.PageRequest;
import jakarta.validation.constraints.*;
import java.util.UUID;
@RestController @Validated @RequestMapping("/api/v1/notifications")
public class NotificationController {
 private final NotificationHistory history;private final IdentityService identity;
 public NotificationController(NotificationHistory history,IdentityService identity){this.history=history;this.identity=identity;}
 @GetMapping public PageResponse<View> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
  return PageResponse.from(history.list(identity.current(jwt).getId(),PageRequest.of(page,size)));
 }
 @GetMapping("/{id}") public View detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return history.detail(identity.current(jwt).getId(),id);}
}
