package com.acme.workforce.reporting.application;
import com.acme.workforce.reporting.api.ReportFilter;
import com.acme.workforce.reporting.infrastructure.ReportFiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.slf4j.*;
import java.util.UUID;
@Service
public class ReportDownloadService {
 private static final Logger log=LoggerFactory.getLogger(ReportDownloadService.class);
 private final ReportingService reports;private final ReportFiles files;
 public ReportDownloadService(ReportingService reports,ReportFiles files){this.reports=reports;this.files=files;}
 @Transactional(propagation=Propagation.NOT_SUPPORTED)
 public byte[] download(UUID user,ReportFilter filter,boolean excel){
  long start=System.nanoTime();var snapshot=reports.snapshot(user,filter);
  // The read-only snapshot transaction has ended before formatting starts.
  var bytes=excel?files.xlsx(snapshot):files.csv(snapshot);
  log.info("Report userId={} year={} month={} type=approved-hours mode={} format={} rows={} durationMs={}",user,filter.year(),filter.month(),filter.effectiveMode(),excel?"xlsx":"csv",snapshot.details().size(),(System.nanoTime()-start)/1000000);
  return bytes;
 }
}
