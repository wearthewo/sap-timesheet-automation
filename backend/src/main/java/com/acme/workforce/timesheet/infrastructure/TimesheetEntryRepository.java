package com.acme.workforce.timesheet.infrastructure;

import com.acme.workforce.timesheet.domain.TimesheetEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, UUID> {
    List<TimesheetEntry> findByTimesheetIdOrderByWorkDateAscIdAsc(UUID timesheetId);
}
