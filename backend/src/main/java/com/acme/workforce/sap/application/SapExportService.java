package com.acme.workforce.sap.application;
import com.acme.workforce.sap.domain.*;
import com.acme.workforce.sap.api.SapDtos.ExportView;
import com.acme.workforce.common.application.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.*;
import java.util.UUID;

@Service
public class SapExportService {
 private static final Logger log=LoggerFactory.getLogger(SapExportService.class);
 private final SapExportTransactions transactions;private final SapTimesheetGateway gateway;
 public SapExportService(SapExportTransactions transactions,SapTimesheetGateway gateway){this.transactions=transactions;this.gateway=gateway;}
 @Transactional(propagation=Propagation.NOT_SUPPORTED)
 public ExportView export(UUID userId,UUID id,long version){
  SapExportTransactions.Reservation reserved;
  try {reserved=transactions.reserve(userId,id,version);}
  catch(DataIntegrityViolationException ex){throw new DomainException(409,"SAP_EXPORT_ALREADY_ATTEMPTED","An export already exists. Refresh its status.");}
  log.info("SAP export reserved exportId={} timesheetId={}",reserved.exportId(),id);
  long start=System.nanoTime();
  SapExportResult result=null;SapGatewayException.Failure failure=null;
  try{result=gateway.export(reserved.payload());}
  catch(SapGatewayException ex){failure=ex.failure();}
  catch(RuntimeException ex){failure=SapGatewayException.Failure.UNKNOWN;}
  var view=transactions.finish(reserved.exportId(),result,failure);
  log.info("SAP export exportId={} timesheetId={} durationMs={} outcome={} externalReference={}",reserved.exportId(),id,(System.nanoTime()-start)/1_000_000,view.status(),view.externalReference());
  if(failure!=null)throw new DomainException(failure==SapGatewayException.Failure.TIMEOUT?504:502,failure.code,failure.message);
  return view;
 }
}
