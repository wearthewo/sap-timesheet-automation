package com.acme.workforce;
import com.acme.workforce.notification.domain.*;
import com.acme.workforce.notification.infrastructure.SmtpEmailGateway;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.MailAuthenticationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpEmailGatewayTest {
 static class SmtpStub implements AutoCloseable {
  final ServerSocket listener;final ExecutorService pool=Executors.newSingleThreadExecutor();
  final BlockingQueue<byte[]> messages=new LinkedBlockingQueue<>();final List<String> recipients=new CopyOnWriteArrayList<>();
  final int rcptCode;final boolean silent;final CountDownLatch release=new CountDownLatch(1);
  volatile boolean running=true;
  SmtpStub(int rcptCode,boolean silent)throws IOException{
   this.rcptCode=rcptCode;this.silent=silent;listener=new ServerSocket(0,10,InetAddress.getLoopbackAddress());
   pool.submit(()->{while(running)try(var socket=listener.accept()){
    if(silent){release.await(3,TimeUnit.SECONDS);continue;}
    var in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
    var out=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true);
    out.print("220 local test SMTP\r\n");out.flush();
    String line;boolean data=false;var body=new StringBuilder();
    while((line=in.readLine())!=null){
     if(data){
      if(line.equals(".")){data=false;messages.add(body.toString().getBytes(StandardCharsets.UTF_8));out.print("250 accepted\r\n");}
      else body.append(line.startsWith("..")?line.substring(1):line).append("\r\n");
     }else if(line.startsWith("EHLO"))out.print("250-local\r\n250 8BITMIME\r\n");
     else if(line.startsWith("RCPT")){recipients.add(line);out.print(rcptCode+" recipient response\r\n");}
     else if(line.equals("DATA")){data=true;out.print("354 end with dot\r\n");}
     else if(line.equals("QUIT")){out.print("221 bye\r\n");out.flush();break;}
     else out.print("250 ok\r\n");
     out.flush();
    }
   }catch(Exception e){if(running)throw new RuntimeException(e);}});
  }
  public void close()throws IOException{running=false;release.countDown();listener.close();pool.shutdownNow();}
 }
 JavaMailSenderImpl sender(int port){
  var sender=new JavaMailSenderImpl();sender.setHost("127.0.0.1");sender.setPort(port);
  sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout","300");
  sender.getJavaMailProperties().setProperty("mail.smtp.timeout","300");
  sender.getJavaMailProperties().setProperty("mail.smtp.writetimeout","300");return sender;
 }
 EmailMessage message(){return new EmailMessage(UUID.randomUUID(),"employee@example.test","Rejected — Δοκιμή","Correct <script>alert('x')</script>\nhttps://app.example/app/timesheets/2026/10");}
 @Test void realSmtpAcceptsUtf8PlainTextAndStableIdentity()throws Exception{
  try(var smtp=new SmtpStub(250,false)){
   var gateway=new SmtpEmailGateway(sender(smtp.listener.getLocalPort()),"noreply@example.test");var message=message();
   assertThat(gateway.send(message).accepted()).isTrue();
   var raw=smtp.messages.poll(2,TimeUnit.SECONDS);assertThat(raw).isNotNull();
   var mime=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(raw));
   assertThat(mime.getSubject()).isEqualTo(message.subject());assertThat(mime.getContent()).isEqualTo(message.body().replace("\n","\r\n")+"\r\n");
   assertThat(mime.isMimeType("text/plain")).isTrue();
   assertThat(mime.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo(message.recipient());
   assertThat(mime.getMessageID()).isEqualTo("<notification."+message.notificationId()+"@workforce.local>");
   assertThat(smtp.messages).isEmpty();assertThat(smtp.recipients).hasSize(1);
  }
 }
 @ParameterizedTest @CsvSource({"450,TEMPORARY","550,RECIPIENT"})
 void smtpResponsesClassified(int code,EmailDeliveryException.Failure expected)throws Exception{
  try(var smtp=new SmtpStub(code,false)){
   var gateway=new SmtpEmailGateway(sender(smtp.listener.getLocalPort()),"noreply@example.test");
   assertThatThrownBy(()->gateway.send(message())).isInstanceOfSatisfying(EmailDeliveryException.class,e->{
    assertThat(e.failure()).isEqualTo(expected);assertThat(e.getMessage()).doesNotContain("recipient response");assertThat(e.getCause()).isNull();
   });
  }
 }
 @Test void socketTimeoutRetryable()throws Exception{
  try(var smtp=new SmtpStub(250,true)){
   var gateway=new SmtpEmailGateway(sender(smtp.listener.getLocalPort()),"noreply@example.test");
   assertThatThrownBy(()->gateway.send(message())).isInstanceOfSatisfying(EmailDeliveryException.class,e->assertThat(e.failure()).isEqualTo(EmailDeliveryException.Failure.TIMEOUT));
  }
 }
 @Test void connectionRefusedRetryable()throws Exception{
  int port;try(var socket=new ServerSocket(0)){port=socket.getLocalPort();}
  var gateway=new SmtpEmailGateway(sender(port),"noreply@example.test");
  assertThatThrownBy(()->gateway.send(message())).isInstanceOfSatisfying(EmailDeliveryException.class,e->assertThat(e.failure()).isEqualTo(EmailDeliveryException.Failure.CONNECTION));
 }
 @Test void authenticationIsPermanent(){
  var sender=mock(JavaMailSenderImpl.class);when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
  doThrow(new MailAuthenticationException("private credentials")).when(sender).send(any(MimeMessage.class));
  var gateway=new SmtpEmailGateway(sender,"noreply@example.test");
  assertThatThrownBy(()->gateway.send(message())).isInstanceOfSatisfying(EmailDeliveryException.class,e->{assertThat(e.failure()).isEqualTo(EmailDeliveryException.Failure.AUTHENTICATION);assertThat(e.getMessage()).doesNotContain("private");});
 }
 @ParameterizedTest @ValueSource(strings={"bad address","a@example.test,b@example.test","a@example.test\r\nBcc:other@example.test"})
 void invalidRecipientsNeverReachSmtp(String recipient){
  var sender=mock(JavaMailSenderImpl.class);var gateway=new SmtpEmailGateway(sender,"noreply@example.test");
  assertThatThrownBy(()->gateway.send(new EmailMessage(UUID.randomUUID(),recipient,"Subject","Body"))).isInstanceOfSatisfying(EmailDeliveryException.class,e->assertThat(e.failure()).isEqualTo(EmailDeliveryException.Failure.RECIPIENT));
  verifyNoInteractions(sender);
 }
 @Test void headerInjectionRejected(){
  var sender=mock(JavaMailSenderImpl.class);var gateway=new SmtpEmailGateway(sender,"noreply@example.test");
  assertThatThrownBy(()->gateway.send(new EmailMessage(UUID.randomUUID(),"a@example.test","Subject\r\nBcc:a@example.test","Body"))).isInstanceOfSatisfying(EmailDeliveryException.class,e->assertThat(e.failure()).isEqualTo(EmailDeliveryException.Failure.CONTENT));
  verifyNoInteractions(sender);
 }
}
