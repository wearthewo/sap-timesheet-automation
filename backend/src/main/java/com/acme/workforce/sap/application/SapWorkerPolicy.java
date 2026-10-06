package com.acme.workforce.sap.application;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;
@Component
public record SapWorkerPolicy(int batchSize,int maxAttempts,Duration initialBackoff,Duration maxBackoff,Duration lease,Duration reconciliationInterval) {
 public SapWorkerPolicy(
  @Value("${app.sap.worker.batch-size}") int batchSize,@Value("${app.sap.worker.max-attempts}") int maxAttempts,
  @Value("${app.sap.worker.initial-backoff}") Duration initialBackoff,@Value("${app.sap.worker.max-backoff}") Duration maxBackoff,
  @Value("${app.sap.worker.processing-timeout}") Duration lease,@Value("${app.sap.worker.reconciliation-interval}") Duration reconciliationInterval){
  if(batchSize<1||batchSize>100||maxAttempts<1||maxAttempts>100)throw new IllegalArgumentException("Invalid SAP worker limits");
  for(var duration:new Duration[]{initialBackoff,maxBackoff,lease,reconciliationInterval})if(duration.isZero()||duration.isNegative())throw new IllegalArgumentException("SAP worker durations must be positive");
  if(maxBackoff.compareTo(initialBackoff)<0)throw new IllegalArgumentException("Maximum backoff must cover initial backoff");
  this.batchSize=batchSize;this.maxAttempts=maxAttempts;this.initialBackoff=initialBackoff;this.maxBackoff=maxBackoff;this.lease=lease;this.reconciliationInterval=reconciliationInterval;
 }
 public Duration backoff(int completedAttempts){
  var delay=initialBackoff;
  for(int i=1;i<completedAttempts && delay.compareTo(maxBackoff)<0;i++){
   if(delay.compareTo(maxBackoff.dividedBy(2))>0)return maxBackoff;
   delay=delay.multipliedBy(2);
  }
  return delay.compareTo(maxBackoff)>0?maxBackoff:delay;
 }
}
