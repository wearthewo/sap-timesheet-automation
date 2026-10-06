package com.acme.workforce.sap.infrastructure;
import com.acme.workforce.sap.application.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
@Configuration @EnableScheduling
@ConditionalOnProperty(name="app.sap.worker.enabled",havingValue="true")
public class SapWorkerScheduling {
 private final SapExportWorker worker;
 public SapWorkerScheduling(SapExportWorker worker,SapWorkerPolicy policy,@Value("${app.sap.connect-timeout}") Duration connect,@Value("${app.sap.response-timeout}") Duration response){
  if(policy.lease().compareTo(connect.plus(response).plusSeconds(10))<=0)throw new IllegalArgumentException("SAP processing lease must exceed HTTP timeouts plus 10 seconds");
  this.worker=worker;
 }
 @Scheduled(fixedDelayString="${app.sap.worker.poll-interval}",initialDelayString="${app.sap.worker.poll-interval}")
 public void poll(){worker.runOnce();}
}
