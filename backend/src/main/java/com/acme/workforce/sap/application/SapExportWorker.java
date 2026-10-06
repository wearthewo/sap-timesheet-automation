package com.acme.workforce.sap.application;
import com.acme.workforce.sap.domain.*;
import com.acme.workforce.sap.infrastructure.SapJobStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.slf4j.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class SapExportWorker {
 private static final Logger log=LoggerFactory.getLogger(SapExportWorker.class);
 private final SapJobStore jobs;private final SapTimesheetGateway gateway;
 private final String workerId=UUID.randomUUID().toString();
 public SapExportWorker(SapJobStore jobs,SapTimesheetGateway gateway){this.jobs=jobs;this.gateway=gateway;}
 @Transactional(propagation=Propagation.NOT_SUPPORTED)
 public void runOnce(){
  var claims=jobs.claim(workerId);
  // Start the bounded claimed batch together so queued local work does not consume its lease.
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
   var futures=claims.stream().map(c->executor.submit(()->process(c))).toList();
   for(var future:futures)try{future.get();}catch(InterruptedException ex){Thread.currentThread().interrupt();return;}catch(ExecutionException ex){log.error("SAP worker operation failed; lease recovery will reconcile. type={}",ex.getCause().getClass().getSimpleName());}
  }
 }
 @Transactional(propagation=Propagation.NOT_SUPPORTED)
 public void process(SapJobStore.Claim c){
  long start=System.nanoTime();SapExportResult result=null;SapGatewayException.Failure failure=null;boolean missing=false;
  try{
   if(c.operation().equals("RECONCILE")){var found=gateway.findByExportIdentity(c.payload().idempotencyKey());result=found.orElse(null);missing=found.isEmpty();}
   else result=gateway.export(c.payload());
  }catch(SapGatewayException ex){failure=ex.failure();}
  catch(RuntimeException ex){failure=SapGatewayException.Failure.UNKNOWN;}
  boolean committed=jobs.finish(c,result,failure,missing);
  log.info("SAP job exportId={} jobId={} timesheetId={} attemptNumber={} identity={} operation={} durationMs={} outcome={} externalReference={}",
   c.exportId(),c.id(),c.timesheetId(),c.execution(),c.payload().idempotencyKey(),c.operation(),(System.nanoTime()-start)/1_000_000,
   !committed?"STALE_RESULT":result!=null?"SUCCEEDED":missing?"NOT_FOUND":failure.code,result==null?null:result.externalReference());
 }
}
