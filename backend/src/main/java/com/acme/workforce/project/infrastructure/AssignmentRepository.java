package com.acme.workforce.project.infrastructure;

import com.acme.workforce.project.domain.ProjectAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AssignmentRepository extends JpaRepository<ProjectAssignment,UUID> {}
