package com.acme.workforce.employee.infrastructure;

import com.acme.workforce.employee.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EmployeeRepository extends JpaRepository<Employee,UUID> {
 Optional<Employee> findByUserId(UUID userId);
}
