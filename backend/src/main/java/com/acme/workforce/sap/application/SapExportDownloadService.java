package com.acme.workforce.sap.application;
import com.acme.workforce.sap.infrastructure.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.slf4j.*;
import java.util.UUID;
@Service
public class SapExportDownloadService {
 private static final Logger log=LoggerFactory.getLogger(SapExportDownloadService.class);
 private final SapExportSnapshotReader snapshots;private final SapExportWorkbook workbook;
 public SapExportDownloadService(SapExportSnapshotReader snapshots,SapExportWorkbook workbook){this.snapshots=snapshots;this.workbook=workbook;}
 public record Download(String filename,byte[] bytes){}
 @Transactional(propagation=Propagation.NOT_SUPPORTED)
 public Download download(UUID user,UUID timesheet){
  var snapshot=snapshots.read(user,timesheet); // REPEATABLE_READ ends before POI formatting.
  var bytes=workbook.generate(snapshot);
  log.info("SAP workbook userId={} timesheetId={} exportId={} entries={}",user,timesheet,snapshot.exportId(),snapshot.payload().entries().size());
  return new Download(snapshot.filename(),bytes);
 }
}
