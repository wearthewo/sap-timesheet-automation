package com.acme.workforce.sap.infrastructure;
import com.acme.workforce.sap.domain.*;
import com.acme.workforce.sap.domain.SapGatewayException.Failure;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.MediaType;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
public class HttpSapTimesheetGateway implements SapTimesheetGateway {
 private final RestClient client; private final ObjectMapper json;private final String baseUrl,token;
 public HttpSapTimesheetGateway(ObjectMapper json,
  @Value("${app.sap.base-url}") String baseUrl,@Value("${app.sap.api-token}") String token,
  @Value("${app.sap.connect-timeout}") Duration connectTimeout,@Value("${app.sap.response-timeout}") Duration responseTimeout){
  if(connectTimeout.isNegative()||connectTimeout.isZero()||responseTimeout.isNegative()||responseTimeout.isZero())throw new IllegalArgumentException("SAP timeouts must be positive");
  this.json=json;this.baseUrl=baseUrl;this.token=token;
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build());
  factory.setReadTimeout(responseTimeout);
  client=RestClient.builder().requestFactory(factory).build();
 }
 @Override public SapExportResult export(ApprovedTimesheetPayload payload){
  if(baseUrl.isBlank()||token.isBlank())throw new SapGatewayException(Failure.CONFIGURATION);
  var entries=payload.entries().stream().map(e->{
   var entry=new LinkedHashMap<String,Object>();
   entry.put("ProjectCode",e.projectCode());entry.put("WorkDate",e.workDate().toString());entry.put("Hours",e.hours().toPlainString());
   entry.put("Description",e.description());return entry;
  }).toList();
  var body=Map.of("ExternalSystemReference",payload.idempotencyKey(),"PersonnelNumber",payload.employeeNumber(),"CalendarYear",payload.year(),"CalendarMonth",payload.month(),"TimeEntries",entries);
  try{
   return client.post().uri(URI.create(baseUrl.replaceAll("/+$","")+"/sap/timesheets"))
    .headers(h->{h.setBearerAuth(token);h.set("Idempotency-Key",payload.idempotencyKey());h.set("X-Correlation-ID",payload.timesheetId().toString());})
    .contentType(MediaType.APPLICATION_JSON).body(body).exchange((request,response)->{
     int status=response.getStatusCode().value();
     if(status==400||status==413||status==422)throw new SapGatewayException(Failure.VALIDATION);
     if(status==401||status==403)throw new SapGatewayException(Failure.AUTHENTICATION);
     if(status==409)throw new SapGatewayException(Failure.DUPLICATE);
     if(status>=500)throw new SapGatewayException(Failure.UNAVAILABLE);
     if(status<200||status>=300)throw new SapGatewayException(Failure.UNKNOWN);
     var bytes=response.getBody().readNBytes(65537);
     if(bytes.length>65536)throw new SapGatewayException(Failure.UNKNOWN);
     tools.jackson.databind.JsonNode node;
     try { node=json.readTree(bytes); } catch(tools.jackson.core.JacksonException ex) { throw new SapGatewayException(Failure.UNKNOWN); }
     String reference=node==null?"":node.path("externalReference").asText("");
     if(node==null||!node.path("success").asBoolean(false)||!reference.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}"))throw new SapGatewayException(Failure.UNKNOWN);
     return new SapExportResult(reference);
    });
  }catch(SapGatewayException ex){throw ex;}
  catch(ResourceAccessException ex){
   for(Throwable cause=ex;cause!=null;cause=cause.getCause()){
    if(cause instanceof HttpConnectTimeoutException||cause instanceof java.net.ConnectException)throw new SapGatewayException(Failure.CONNECTION);
    if(cause instanceof HttpTimeoutException||cause instanceof java.net.SocketTimeoutException)throw new SapGatewayException(Failure.TIMEOUT);
   }
   throw new SapGatewayException(Failure.UNAVAILABLE);
  }catch(RuntimeException ex){throw new SapGatewayException(Failure.UNKNOWN);}
 }
 @Override public Optional<SapExportResult> findByExportIdentity(String identity){
  if(baseUrl.isBlank()||token.isBlank())throw new SapGatewayException(Failure.CONFIGURATION);
  try{
   return client.get().uri(URI.create(baseUrl.replaceAll("/+$","")+"/sap/timesheets/by-identity?identity="+java.net.URLEncoder.encode(identity,java.nio.charset.StandardCharsets.UTF_8)))
    .headers(h->{h.setBearerAuth(token);h.set("X-Correlation-ID",identity);})
    .exchange((request,response)->{
     int status=response.getStatusCode().value();
     if(status==404)return Optional.empty();
     if(status==401||status==403)throw new SapGatewayException(Failure.AUTHENTICATION);
     if(status>=500)throw new SapGatewayException(Failure.UNAVAILABLE);
     if(status!=200)throw new SapGatewayException(Failure.UNKNOWN);
     var bytes=response.getBody().readNBytes(65537);
     if(bytes.length>65536)throw new SapGatewayException(Failure.UNKNOWN);
     tools.jackson.databind.JsonNode node;
     try{node=json.readTree(bytes);}catch(tools.jackson.core.JacksonException ex){throw new SapGatewayException(Failure.UNKNOWN);}
     String ref=node==null?"":node.path("externalReference").asText("");
     if(node==null||!node.path("success").asBoolean(false)||!node.path("exportIdentity").asText().equals(identity)||!ref.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}"))throw new SapGatewayException(Failure.UNKNOWN);
     return Optional.of(new SapExportResult(ref));
    });
  }catch(SapGatewayException ex){throw ex;}
  catch(RuntimeException ex){throw new SapGatewayException(Failure.UNKNOWN);}
 }
}