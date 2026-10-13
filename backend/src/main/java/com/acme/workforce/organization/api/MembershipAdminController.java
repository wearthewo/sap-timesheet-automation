package com.acme.workforce.organization.api;
import com.acme.workforce.organization.application.MembershipAdministration;
import com.acme.workforce.organization.application.MembershipAdministration.*;
import com.acme.workforce.common.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;
@RestController @Validated @RequestMapping("/api/v1/admin/members") @PreAuthorize("@applicationAuthorization.isAdmin(authentication)")
public class MembershipAdminController {
 private final MembershipAdministration members;public MembershipAdminController(MembershipAdministration members){this.members=members;}
 @GetMapping public PageResponse<Member> list(@RequestParam(defaultValue="") @Size(max=200) String search,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return PageResponse.from(members.list(search,status,PageRequest.of(page,size)));}
 @PutMapping("/{id}") public Member change(@PathVariable UUID id,@Valid @RequestBody Change r){return members.change(id,r);}
}
