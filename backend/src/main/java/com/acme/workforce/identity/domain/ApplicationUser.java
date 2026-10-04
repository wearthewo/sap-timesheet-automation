package com.acme.workforce.identity.domain;

import com.acme.workforce.common.domain.TimestampedEntity;
import jakarta.persistence.*;
@Entity @Table(name="app_users")
public class ApplicationUser extends TimestampedEntity {
 @Column(nullable=false, unique=true) private String externalSubject;
 @Column(nullable=false, length=320) private String email;
 @Column(nullable=false) private String displayName;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private UserRole role;
 protected ApplicationUser() {}
 public String getExternalSubject() { return externalSubject; }
 public String getEmail() { return email; }
 public String getDisplayName() { return displayName; }
 public UserRole getRole() { return role; }
}
