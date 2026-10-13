package com.acme.workforce.organization.application;
import com.acme.workforce.common.application.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** Reconstructible only with the server secret, so the durable outbox never stores a bearer link. */
@Component
public class InvitationTokens {
 private final String key;
 public InvitationTokens(@Value("${app.invitations.signing-key:}") String key){this.key=key;}
 public String token(UUID id){
  if(key.length()<32)throw new DomainException(503,"INVITATIONS_NOT_CONFIGURED","Invitations are not configured. Contact the system operator.");
  try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return id+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(("workforce-invitation-v1:"+id).getBytes(StandardCharsets.UTF_8)));}
  catch(GeneralSecurityException e){throw new IllegalStateException("Invitation signing unavailable");}
 }
 public String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException("Invitation digest unavailable");}}
}
