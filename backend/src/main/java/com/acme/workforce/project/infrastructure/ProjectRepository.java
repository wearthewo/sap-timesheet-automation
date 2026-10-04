package com.acme.workforce.project.infrastructure;

import com.acme.workforce.project.domain.Project;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.time.LocalDate;
import java.util.UUID;
public interface ProjectRepository extends JpaRepository<Project,UUID> {
 @Query("select p from Project p where p.active=true and exists (select a.id from ProjectAssignment a where a.projectId=p.id and a.employeeId=:employee and a.active=true and a.validFrom<=:today and (a.validTo is null or a.validTo>=:today))")
 Page<Project> assigned(@Param("employee") UUID employee,@Param("today") LocalDate today,Pageable pageable);
}
