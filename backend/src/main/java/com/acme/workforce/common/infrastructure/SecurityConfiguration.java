package com.acme.workforce.common.infrastructure;

import com.acme.workforce.common.api.ApiError;
import com.acme.workforce.identity.application.IdentityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;
@Configuration @EnableMethodSecurity
public class SecurityConfiguration {
 @Bean
 SecurityFilterChain security(HttpSecurity http,ObjectMapper mapper,IdentityService identity,com.acme.workforce.organization.application.OrganizationAccess organization,
  @Value("${app.cors-allowed-origins}") String origins) throws Exception {
  var cors=new CorsConfiguration();
  cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::strip).filter(s->!s.isEmpty()).toList());
  cors.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
  cors.setAllowedHeaders(List.of("Authorization","Content-Type","X-Timesheet-Version","X-Organization-Id","Idempotency-Key"));
  cors.setAllowCredentials(false);
  var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/api/**",cors);
  http.csrf(csrf->csrf.disable()).cors(c->c.configurationSource(source))
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/actuator/health/**","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll().anyRequest().authenticated())
   .oauth2ResourceServer(o->o.jwt(j->{})
    .authenticationEntryPoint((req,res,ex)->write(mapper,req,res,401,"UNAUTHENTICATED","Authentication required")))
   .exceptionHandling(e->e
    .authenticationEntryPoint((req,res,ex)->write(mapper,req,res,401,"UNAUTHENTICATED","Authentication required"))
    .accessDeniedHandler((req,res,ex)->write(mapper,req,res,403,"FORBIDDEN","Access denied")))
   .addFilterAfter(new OncePerRequestFilter() {
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
     if(req.getRequestURI().startsWith("/api/") && SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
      var user=identity.current(token.getToken());
      boolean identityOnly=req.getRequestURI().equals("/api/v1/auth/organizations") || (req.getRequestURI().equals("/api/v1/auth/me") && req.getHeader("X-Organization-Id")==null);
      if(!identityOnly) try {
       com.acme.workforce.identity.application.VerifiedIdentity.requireVerifiedEmail(token.getToken());
       boolean creatingOrganization=req.getMethod().equals("POST") && req.getRequestURI().equals("/api/v1/organizations");
       if(!creatingOrganization) organization.role(user.getId());
      }
      catch(com.acme.workforce.common.application.DomainException ex) { write(mapper,req,res,ex.status(),ex.code(),ex.getMessage()); return; }
     }
     chain.doFilter(req,res);
    }
   },BearerTokenAuthenticationFilter.class);
  return http.build();
 }
 private static void write(ObjectMapper mapper,HttpServletRequest req,HttpServletResponse res,int status,String code,String message) throws IOException {
  res.setStatus(status); res.setContentType("application/json");
  if(status==401) res.setHeader("WWW-Authenticate","Bearer");
  mapper.writeValue(res.getOutputStream(),ApiError.of(status,code,message,req.getRequestURI()));
 }
}
