package com.acme.workforce.identity.application;

import com.acme.workforce.identity.domain.ApplicationUser;
import com.acme.workforce.identity.infrastructure.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.*;
import java.util.UUID;
@Service
public class IdentityService {
 private static final Logger log=LoggerFactory.getLogger(IdentityService.class);
 private final UserRepository users;
 public IdentityService(UserRepository users) { this.users=users; }
 @Transactional
 public ApplicationUser current(Jwt jwt) {
  return users.findByExternalSubject(jwt.getSubject()).orElseGet(() -> {
   String email=claim(jwt,"email","");
   String name=claim(jwt,"name",claim(jwt,"preferred_username","Employee"));
   UUID id=UUID.randomUUID();
   if(users.provision(id,jwt.getSubject(),email,name)>0) log.info("Provisioned application user id={}",id);
   return users.findByExternalSubject(jwt.getSubject()).orElseThrow();
  });
 }
 private String claim(Jwt jwt,String name,String fallback) {
  String value=jwt.getClaimAsString(name); return value==null || value.isBlank()?fallback:value;
 }
}
