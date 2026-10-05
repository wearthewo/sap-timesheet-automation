package com.acme.workforce.timesheet.infrastructure;

import com.acme.workforce.timesheet.domain.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;

public interface TimesheetRepository extends JpaRepository<Timesheet, UUID> {
    Optional<Timesheet> findByEmployeeIdAndYearAndMonth(UUID employeeId, int year, int month);
    Optional<Timesheet> findByIdAndEmployeeId(UUID id, UUID employeeId);
    Page<Timesheet> findByEmployeeId(UUID employeeId, Pageable pageable);
}
