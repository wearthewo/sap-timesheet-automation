package com.acme.workforce.sap.application;
import com.acme.workforce.sap.api.SapDtos.ExportView;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service
public class SapExportService {
 private final SapExportTransactions transactions;
 public SapExportService(SapExportTransactions transactions){this.transactions=transactions;}
 public ExportView export(UUID userId,UUID id,long version){return transactions.enqueue(userId,id,version);}
 public ExportView retry(UUID userId,UUID id){transactions.manual(userId,id,false);return transactions.state(userId,id);}
 public ExportView reconcile(UUID userId,UUID id){transactions.manual(userId,id,true);return transactions.state(userId,id);}
}
