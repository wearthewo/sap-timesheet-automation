package com.acme.workforce.timesheet.domain;

import com.acme.workforce.common.domain.TimestampedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "timesheet_entries")
public class TimesheetEntry extends TimestampedEntity {
    @Column(nullable = false) private UUID timesheetId;
    @Column(nullable = false) private UUID projectId;
    @Column(nullable = false) private LocalDate workDate;
    @Column(nullable = false, precision = 7, scale = 2) private BigDecimal hours;
    @Column(length = 2000) private String description;

    protected TimesheetEntry() {}
    public TimesheetEntry(UUID timesheetId, UUID projectId, LocalDate workDate, BigDecimal hours, String description) {
        this.timesheetId = timesheetId;
        edit(projectId, workDate, hours, description);
    }
    public void edit(UUID projectId, LocalDate workDate, BigDecimal hours, String description) {
        this.projectId = projectId;
        this.workDate = workDate;
        this.hours = hours;
        this.description = description;
    }
    public UUID getTimesheetId() { return timesheetId; }
    public UUID getProjectId() { return projectId; }
    public LocalDate getWorkDate() { return workDate; }
    public BigDecimal getHours() { return hours; }
    public String getDescription() { return description; }
}
