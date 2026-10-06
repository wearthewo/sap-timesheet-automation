package com.acme.workforce.sap.infrastructure;
import com.acme.workforce.sap.domain.SapTimesheetExport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SapExportRepository extends JpaRepository<SapTimesheetExport,UUID> {
 Optional<SapTimesheetExport> findByTimesheetId(UUID timesheetId);
 List<SapTimesheetExport> findByTimesheetIdIn(Collection<UUID> timesheetIds);
}
