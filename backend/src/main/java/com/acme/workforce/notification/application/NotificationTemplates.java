package com.acme.workforce.notification.application;
import org.springframework.stereotype.Component;
import java.time.*;
@Component
public class NotificationTemplates {
 private final NotificationPolicy policy;
 public NotificationTemplates(NotificationPolicy policy){this.policy=policy;}
 public record Content(String subject,String body){}
 public String link(String path){return policy.publicBaseUrl()+path;}
 public Content decision(String name,YearMonth month,boolean approved,String comment,Instant at){
  return new Content("Timesheet "+(approved?"approved":"rejected")+" — "+month,
   "Hello "+name+",\n\nYour "+month+" timesheet was "+(approved?"approved":"rejected")+" on "+at.atZone(policy.zone())+".\n"+
   (comment==null||comment.isBlank()?"":"\nManager comment:\n"+comment+"\n")+
   (approved?"\nYour approved timesheet is read-only.":"\nPlease reopen your timesheet, correct the entries and resubmit for review.")+
   "\n\n"+link("/app/timesheets/"+month.getYear()+"/"+month.getMonthValue()));
 }
 public Content timesheet(String name,YearMonth month,String state){
  return new Content("Timesheet reminder — "+month,"Hello "+name+",\n\nYour "+month+" timesheet is "+state+". Please complete and submit it for manager review.\n\n"+link("/app/timesheets/"+month.getYear()+"/"+month.getMonthValue()));
 }
 public Content manager(YearMonth month,long count,String entries){
  return new Content(count+" timesheets waiting for approval — "+month,
   month+" approvals pending:\n\n"+entries+(count>50?"\nAdditional timesheets are available in the application.":"")+"\n\n"+link("/app/approvals"));
 }
 public Content sap(String employee,YearMonth month,String status,String safeCode,java.util.UUID timesheet){
  boolean unknown=status.equals("UNKNOWN");
  return new Content("SAP export "+status+" — "+month,
   (unknown?"SAP export outcome could not be confirmed. Reconciliation is required.":"Automatic SAP export failed and requires review.")+
   "\n\nEmployee: "+employee+"\nMonth: "+month+"\nStatus: "+status+"\nCode: "+safeCode+
   (unknown?"\nAutomatic reconciliation may resolve this. Review the current state; do not blindly retry.":"\nReview the current export state before taking action.")+
   "\n\n"+link("/app/sap/"+timesheet));
 }
 public Content finance(YearMonth month,long incomplete,long failed,long unknown){
  return new Content("Monthly operational review — "+month,
   "Please review "+month+".\n\nMissing or unfinished timesheets: "+incomplete+"\nSAP FAILED: "+failed+"\nSAP UNKNOWN: "+unknown+
   "\n\n"+link("/app/reports/monthly?year="+month.getYear()+"&month="+month.getMonthValue()));
 }
}
