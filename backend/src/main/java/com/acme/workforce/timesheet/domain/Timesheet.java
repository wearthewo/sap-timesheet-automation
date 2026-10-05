package com.acme.workforce.timesheet.domain;

import com.acme.workforce.common.domain.TimestampedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "timesheets")
public class Timesheet extends TimestampedEntity {
    @Column(nullable = false) private UUID employeeId;
    @Column(nullable = false) private int year;
    @Column(nullable = false) private int month;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TimesheetStatus status = TimesheetStatus.DRAFT;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalHours = new BigDecimal("0.00");
    private Instant submittedAt;
    @Version @Column(nullable = false) private long version;

    protected Timesheet() {}
    public Timesheet(UUID employeeId, int year, int month) {
        this.employeeId = employeeId;
        this.year = year;
        this.month = month;
    }
    public void recalculate(BigDecimal total) {
        totalHours = total;
        // Dirty the aggregate even for description-only edits or equal-hour changes.
        // TimestampedEntity assigns the actual update timestamp during flush.
        Instant now = Instant.now();
        updatedAt = now.equals(updatedAt) ? now.plusNanos(1000) : now;
    }
    public void submit() {
        status = TimesheetStatus.SUBMITTED;
        submittedAt = Instant.now();
    }
    public UUID getEmployeeId() { return employeeId; }
    public int getYear() { return year; }
    public int getMonth() { return month; }
    public TimesheetStatus getStatus() { return status; }
    public BigDecimal getTotalHours() { return totalHours; }
    public Instant getSubmittedAt() { return submittedAt; }
    public long getVersion() { return version; }
}
