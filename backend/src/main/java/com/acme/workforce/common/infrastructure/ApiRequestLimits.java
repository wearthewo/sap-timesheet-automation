package com.acme.workforce.common.infrastructure;

import com.acme.workforce.common.application.DomainException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;

/** Per-process protection, not a substitute for ingress/IP limits across replicas. */
public final class ApiRequestLimits {
 private static final Set<String> SENSITIVE=Set.of("/api/v1/organizations","/api/v1/admin/invitations","/api/v1/invitations/accept","/api/v1/invitations/preview");
 private record Window(long end,int count) {}
 private final Map<String,Window> windows=new HashMap<>();
 private final Clock clock; private final int maxBytes,perMinute,maxSubjects;
 public ApiRequestLimits(Clock clock,int maxBytes,int perMinute,int maxSubjects){
  if(maxBytes<1||maxBytes>1048576||perMinute<1||maxSubjects<1)throw new IllegalArgumentException("Invalid API request limits");
  this.clock=clock;this.maxBytes=maxBytes;this.perMinute=perMinute;this.maxSubjects=maxSubjects;
 }
 public HttpServletRequest protect(HttpServletRequest request,String subject)throws IOException{
  if(!Set.of("POST","PUT","PATCH").contains(request.getMethod()))return request;
  if(request.getContentLengthLong()>maxBytes)throw tooLarge();
  byte[] body=request.getInputStream().readNBytes(maxBytes+1);
  if(body.length>maxBytes)throw tooLarge();
  if(SENSITIVE.contains(request.getRequestURI()))take(subject);
  return new HttpServletRequestWrapper(request){
   @Override public int getContentLength(){return body.length;}
   @Override public long getContentLengthLong(){return body.length;}
   @Override public ServletInputStream getInputStream(){
    var input=new ByteArrayInputStream(body);
    return new ServletInputStream(){
     @Override public int read(){return input.read();}
     @Override public int read(byte[] b,int off,int len){return input.read(b,off,len);}
     @Override public boolean isFinished(){return input.available()==0;}
     @Override public boolean isReady(){return true;}
     @Override public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous API request");}
    };
   }
   @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
  };
 }
 private synchronized void take(String subject){
  long now=clock.millis();var window=windows.get(subject);
  if(window==null||window.end()<=now){
   windows.entrySet().removeIf(e->e.getValue().end()<=now);
   if(windows.size()>=maxSubjects)throw limited();
   windows.put(subject,new Window(now+60000,1));
  }else{
   if(window.count()>=perMinute)throw limited();
   windows.put(subject,new Window(window.end(),window.count()+1));
  }
 }
 private static DomainException tooLarge(){return new DomainException(413,"REQUEST_TOO_LARGE","Request body exceeds the allowed size.");}
 private static DomainException limited(){return new DomainException(429,"RATE_LIMITED","Too many invitation or organization requests. Try again in one minute.");}
}
