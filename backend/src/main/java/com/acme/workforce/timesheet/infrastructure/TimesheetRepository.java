package com.acme.workforce.timesheet.infrastructure;

import com.acme.workforce.timesheet.domain.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;

public interface TimesheetRepository extends JpaRepository<Timesheet, UUID> {
    org.springframework.data.domain.Page<Timesheet> findByOrganizationIdAndStatus(UUID organizationId,com.acme.workforce.timesheet.domain.TimesheetStatus status, org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select t from Timesheet t, Employee e where t.employeeId=e.id and e.managerId=:managerId and e.id<>:managerId and t.status=com.acme.workforce.timesheet.domain.TimesheetStatus.PENDING_APPROVAL")
    Page<Timesheet> pendingForManager(@org.springframework.data.repository.query.Param("managerId") UUID managerId, Pageable pageable);
    Optional<Timesheet> findByEmployeeIdAndYearAndMonth(UUID employeeId, int year, int month);
    Optional<Timesheet> findByIdAndEmployeeId(UUID id, UUID employeeId);
    Page<Timesheet> findByEmployeeId(UUID employeeId, Pageable pageable);
 Optional<Timesheet> findByIdAndOrganizationId(UUID id,UUID organizationId);
}
