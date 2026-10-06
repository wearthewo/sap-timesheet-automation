package com.acme.workforce.sap.domain;
import com.acme.workforce.common.domain.TimestampedEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="sap_timesheet_exports")
public class SapTimesheetExport extends TimestampedEntity {
 @Column(nullable=false,unique=true) private UUID timesheetId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private SapExportStatus status;
 @Column(length=128) private String externalReference;
 @Column(length=80) private String failureCode;
 @Column(length=500) private String failureMessage;
 private Instant exportedAt;
 protected SapTimesheetExport(){}
 public SapTimesheetExport(UUID timesheetId){this.timesheetId=timesheetId;this.status=SapExportStatus.QUEUED;}
 public UUID getTimesheetId(){return timesheetId;} public SapExportStatus getStatus(){return status;}
 public String getExternalReference(){return externalReference;} public String getFailureCode(){return failureCode;}
 public String getFailureMessage(){return failureMessage;} public Instant getExportedAt(){return exportedAt;}
}
