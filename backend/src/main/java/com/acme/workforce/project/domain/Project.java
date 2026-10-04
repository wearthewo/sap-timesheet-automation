package com.acme.workforce.project.domain;

import com.acme.workforce.common.domain.TimestampedEntity;
import jakarta.persistence.*;
@Entity @Table(name="projects")
public class Project extends TimestampedEntity {
 @Column(nullable=false,unique=true,length=50) private String code;
 @Column(nullable=false,length=200) private String name;
 @Column(length=2000) private String description;
 @Column(nullable=false) private boolean active;
 protected Project() {}
 public Project(String code,String name,String description,boolean active) { update(code,name,description,active); }
 public void update(String code,String name,String description,boolean active) { this.code=code.strip(); this.name=name.strip(); this.description=description; this.active=active; }
 public String getCode() { return code; }
 public String getName() { return name; }
 public String getDescription() { return description; }
 public boolean isActive() { return active; }
}
