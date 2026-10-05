package com.acme.workforce.timesheet.application;

import com.acme.workforce.common.application.DomainException;
import com.acme.workforce.project.domain.*;
import com.acme.workforce.timesheet.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Component
public class TimesheetPolicy {
    private final BigDecimal maxDailyHours;
    public TimesheetPolicy(@Value("${app.timesheets.max-daily-hours:24}") BigDecimal maximum) {
        if (maximum.signum() <= 0 || maximum.compareTo(new BigDecimal("99999.99")) > 0 || maximum.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("MAX_DAILY_HOURS must be positive, at most 99999.99, with at most two decimal places");
        }
        this.maxDailyHours = maximum.setScale(2);
    }
    public BigDecimal maximum() { return maxDailyHours; }
    public void checkDate(Timesheet sheet, LocalDate date) {
        if (!YearMonth.from(date).equals(YearMonth.of(sheet.getYear(), sheet.getMonth()))) {
            throw new DomainException(400, "INVALID_WORK_DATE", "Work date must be in this timesheet's month");
        }
    }
    public boolean available(Project project, List<ProjectAssignment> assignments, LocalDate date) {
        return project.isActive() && assignments.stream().anyMatch(a -> a.getProjectId().equals(project.getId())
                && a.isActive() && !date.isBefore(a.getValidFrom())
                && (a.getValidTo() == null || !date.isAfter(a.getValidTo())));
    }
    public void checkProject(Project project, List<ProjectAssignment> assignments, LocalDate date) {
        if (!project.isActive()) throw new DomainException(400, "PROJECT_INACTIVE", "This project is inactive");
        var matching = assignments.stream().filter(a -> a.getProjectId().equals(project.getId())).toList();
        if (matching.isEmpty()) throw new DomainException(400, "PROJECT_NOT_ASSIGNED", "This project is not assigned to you");
        if (matching.stream().noneMatch(ProjectAssignment::isActive)) {
            throw new DomainException(400, "PROJECT_ASSIGNMENT_INACTIVE", "Your project assignment is inactive");
        }
        if (!available(project, matching, date)) {
            throw new DomainException(400, "PROJECT_NOT_ASSIGNED", "This project is not assigned to you on the work date");
        }
    }
    public SortedMap<LocalDate, BigDecimal> totals(List<TimesheetEntry> entries) {
        SortedMap<LocalDate, BigDecimal> totals = new TreeMap<>();
        for (var entry : entries) {
            if (entry.getHours().signum() <= 0 || entry.getHours().stripTrailingZeros().scale() > 2) {
                throw new DomainException(400, "VALIDATION_ERROR", "Hours must be positive with at most two decimal places");
            }
            totals.merge(entry.getWorkDate(), entry.getHours(), BigDecimal::add);
        }
        for (var day : totals.entrySet()) {
            if (day.getValue().compareTo(maxDailyHours) > 0) {
                throw new DomainException(400, "DAILY_HOURS_EXCEEDED", "Total hours on " + day.getKey() + " exceed " + maxDailyHours);
            }
        }
        return totals;
    }
}
