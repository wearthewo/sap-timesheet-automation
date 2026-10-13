package com.acme.workforce.employee.domain;

import com.acme.workforce.common.domain.TimestampedEntity;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="employees")
public class Employee extends TimestampedEntity {
 @Column(nullable=false,updatable=false) private java.util.UUID organizationId;
 public java.util.UUID getOrganizationId() { return organizationId; }
 private UUID userId;
 @Column(length=255) private String displayName;
 @Version private long version;
 @Column(nullable=false,unique=true,length=100) private String employeeNumber;
 @Column(nullable=false) private boolean active;
 private UUID managerId;
 protected Employee() {}
 public Employee(UUID organizationId,UUID userId,String employeeNumber,UUID managerId) { this.organizationId=organizationId; this.userId=userId; this.employeeNumber=employeeNumber; this.managerId=managerId; this.active=true; }
 public UUID getUserId() { return userId; }
 public String getEmployeeNumber() { return employeeNumber; }
 public boolean isActive() { return active; }
 public UUID getManagerId() { return managerId; }
}
