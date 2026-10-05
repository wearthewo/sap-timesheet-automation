package com.acme.workforce.timesheet.api;

import com.acme.workforce.common.api.PageResponse;
import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.timesheet.application.TimesheetService;
import com.acme.workforce.timesheet.api.TimesheetDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;

@RestController
@Validated
@RequestMapping("/api/v1/timesheets")
public class TimesheetController {
    private final TimesheetService timesheets;
    private final IdentityService identity;
    public TimesheetController(TimesheetService timesheets, IdentityService identity) {
        this.timesheets = timesheets; this.identity = identity;
    }
    private UUID user(Jwt jwt) { return identity.current(jwt).getId(); }
    @GetMapping
    public PageResponse<Summary> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return PageResponse.from(timesheets.list(user(jwt), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "year", "month"))));
    }
    @GetMapping("/{year}/{month}")
    public Detail month(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(1) @Max(9999) int year, @PathVariable @Min(1) @Max(12) int month) {
        return timesheets.month(user(jwt), year, month);
    }
    @PostMapping
    public ResponseEntity<Detail> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTimesheet request) {
        var result = timesheets.create(user(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/timesheets/" + result.year() + "/" + result.month())).body(result);
    }
    @GetMapping("/{id}/available-projects")
    public List<ProjectReference> available(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestParam LocalDate workDate) {
        return timesheets.available(user(jwt), id, workDate);
    }
    @PostMapping("/{id}/entries")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Detail add(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestHeader("X-Timesheet-Version") @Min(0) long version, @Valid @RequestBody WriteEntry request) {
        return timesheets.add(user(jwt), id, version, request);
    }
    @PutMapping("/{id}/entries/{entryId}")
    public Detail edit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID entryId,
            @RequestHeader("X-Timesheet-Version") @Min(0) long version, @Valid @RequestBody WriteEntry request) {
        return timesheets.edit(user(jwt), id, entryId, version, request);
    }
    @DeleteMapping("/{id}/entries/{entryId}")
    public Detail delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID entryId,
            @RequestHeader("X-Timesheet-Version") @Min(0) long version) {
        return timesheets.delete(user(jwt), id, entryId, version);
    }
    @PostMapping("/{id}/submit")
    public Detail submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestHeader("X-Timesheet-Version") @Min(0) long version) {
        return timesheets.submit(user(jwt), id, version);
    }
}
