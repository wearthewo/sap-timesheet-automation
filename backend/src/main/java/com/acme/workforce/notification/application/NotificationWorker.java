package com.acme.workforce.notification.application;
import com.acme.workforce.notification.domain.*;
import com.acme.workforce.notification.infrastructure.NotificationStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.slf4j.*;
import java.util.*;
import java.util.concurrent.*;
@Service
public class NotificationWorker {
 private static final Logger log=LoggerFactory.getLogger(NotificationWorker.class);
 private final NotificationStore store;private final EmailGateway gateway;private final com.acme.workforce.organization.application.Invitations invitations;private final String workerId=UUID.randomUUID().toString();
 public NotificationWorker(NotificationStore store,EmailGateway gateway,com.acme.workforce.organization.application.Invitations invitations){this.store=store;this.gateway=gateway;this.invitations=invitations;}
 @Transactional(propagation=Propagation.NOT_SUPPORTED)
 public void runOnce(){
  var claims=store.claim(workerId);
  try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
   var futures=claims.stream().map(c->pool.submit(()->deliver(c))).toList();
   for(var f:futures)try{f.get();}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
   catch(ExecutionException e){log.error("Notification worker result failed; lease recovery will handle it. type={}",e.getCause().getClass().getSimpleName());}
  }
 }
 @Transactional(propagation=Propagation.NOT_SUPPORTED)
 public void deliver(NotificationStore.Claim c){
  long start=System.nanoTime();boolean accepted=false;EmailDeliveryException.Failure failure=null;
  try{var message=c.type()==NotificationType.ORGANIZATION_INVITATION?invitations.delivery(c.id(),c.organizationId()):c.message();accepted=gateway.send(message).accepted();if(!accepted)failure=EmailDeliveryException.Failure.UNKNOWN;}
  catch(EmailDeliveryException e){failure=e.failure();}
  catch(RuntimeException e){failure=EmailDeliveryException.Failure.UNKNOWN;}
  boolean committed=store.finish(c,accepted,failure);
  log.info("Notification organizationId={} id={} type={} recipientUserId={} attempt={} durationMs={} outcome={} errorCode={}",
   c.organizationId(),c.id(),c.type(),c.recipientId(),c.attempt(),(System.nanoTime()-start)/1000000,!committed?"STALE_RESULT":accepted?"SENT":"DELIVERY_FAILED",failure==null?null:failure.code);
 }
}
