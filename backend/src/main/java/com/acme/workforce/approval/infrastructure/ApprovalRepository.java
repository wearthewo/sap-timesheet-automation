package com.acme.workforce.approval.infrastructure;
import com.acme.workforce.approval.domain.TimesheetApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ApprovalRepository extends JpaRepository<TimesheetApproval,UUID> {
 List<TimesheetApproval> findByTimesheetIdOrderByDecidedAtAscIdAsc(UUID id);
}
