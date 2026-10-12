package com.acme.workforce.employee.infrastructure;

import com.acme.workforce.employee.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EmployeeRepository extends JpaRepository<Employee,UUID> {
 Optional<Employee> findByUserIdAndOrganizationId(UUID userId,UUID organizationId);
 Optional<Employee> findByIdAndOrganizationId(UUID id,UUID organizationId);
 boolean existsByIdAndOrganizationId(UUID id,UUID organizationId);
}
