package com.acme.workforce.notification.infrastructure;
import com.acme.workforce.notification.domain.*;
import com.acme.workforce.notification.domain.EmailDeliveryException.Failure;
import org.springframework.stereotype.Component;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.eclipse.angus.mail.smtp.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Component
public class SmtpEmailGateway implements EmailGateway {
 private final JavaMailSender sender;private final String from;
 public SmtpEmailGateway(JavaMailSender sender,@Value("${app.notifications.mail-from}") String from){this.sender=sender;this.from=from;}
 @Override public EmailDeliveryResult send(EmailMessage message){
  InternetAddress recipient;
  try{recipient=address(message.recipient());}catch(MessagingException|IllegalArgumentException e){throw new EmailDeliveryException(Failure.RECIPIENT);}
  try{
   if(message.subject()==null||message.subject().contains("\r")||message.subject().contains("\n")||message.body()==null)throw new EmailDeliveryException(Failure.CONTENT);
   var mime=sender.createMimeMessage();
   // Spring's sender preserves an explicitly supplied Message-ID across saveChanges().
   mime.setHeader("Message-ID","<notification."+message.notificationId()+"@workforce.local>");
   mime.setFrom(address(from));mime.setRecipient(Message.RecipientType.TO,recipient);
   mime.setSubject(message.subject(),StandardCharsets.UTF_8.name());mime.setText(message.body(),StandardCharsets.UTF_8.name());
   sender.send(mime);return new EmailDeliveryResult(true);
  }catch(EmailDeliveryException e){throw e;}
  catch(MailException|MessagingException|IllegalArgumentException e){throw new EmailDeliveryException(classify(e));}
 }
 private InternetAddress address(String value)throws AddressException{
  if(value==null||value.contains("\r")||value.contains("\n"))throw new AddressException("Invalid address");
  var addresses=InternetAddress.parse(value,true);
  if(addresses.length!=1||addresses[0].isGroup())throw new AddressException("One recipient is required");
  addresses[0].validate();return addresses[0];
 }
 private Failure classify(Throwable error){
  var queue=new ArrayDeque<Throwable>();var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());queue.add(error);
  Failure fallback=Failure.UNKNOWN;
  while(!queue.isEmpty()){
   var e=queue.removeFirst();if(!seen.add(e))continue;
   if(e instanceof MailAuthenticationException || e instanceof AuthenticationFailedException)return Failure.AUTHENTICATION;
   int code=e instanceof SMTPAddressFailedException a?a.getReturnCode():e instanceof SMTPSendFailedException s?s.getReturnCode():0;
   if(code>=400&&code<500)return Failure.TEMPORARY;
   if(code>=500)return e instanceof SMTPAddressFailedException?Failure.RECIPIENT:Failure.PERMANENT;
   if(e instanceof AddressException||e instanceof MailParseException||e instanceof MailPreparationException)return Failure.CONTENT;
   if(e instanceof SocketTimeoutException)fallback=Failure.TIMEOUT;
   else if(e instanceof ConnectException||e instanceof UnknownHostException)fallback=Failure.CONNECTION;
   if(e instanceof MailSendException s)queue.addAll(s.getFailedMessages().values());
   if(e instanceof MessagingException m && m.getNextException()!=null)queue.add(m.getNextException());
   if(e.getCause()!=null)queue.add(e.getCause());
  }
  return fallback;
 }
}
