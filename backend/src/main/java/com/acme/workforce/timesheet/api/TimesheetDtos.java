package com.acme.workforce.timesheet.api;

import com.acme.workforce.timesheet.domain.TimesheetStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class TimesheetDtos {
    private TimesheetDtos() {}
    public record CreateTimesheet(@Min(1) @Max(9999) int year, @Min(1) @Max(12) int month) {}
    public record WriteEntry(
            @NotNull UUID projectId,
            @NotNull LocalDate workDate,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 2) BigDecimal hours,
            @Size(max = 2000) String description) {}
    public record ProjectReference(UUID id, String code, String name) {}
    public record EntryView(UUID id, ProjectReference project, LocalDate workDate,
            @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "6.50") @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal hours, String description) {}
    public record DailyTotal(LocalDate workDate,
            @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "6.50") @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal hours) {}
    public record Summary(UUID id, int year, int month, TimesheetStatus status,
            @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "6.50") @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalHours,
            Instant submittedAt, long version) {}
    public record Detail(UUID id, int year, int month, TimesheetStatus status,
            @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "6.50") @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalHours,
            Instant submittedAt, long version, List<EntryView> entries, List<DailyTotal> dailyTotals,
            @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "6.50") @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal maxDailyHours, List<com.acme.workforce.approval.api.ApprovalDtos.DecisionView> approvalHistory) {}
}
