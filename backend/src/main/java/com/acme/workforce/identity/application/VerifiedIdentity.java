package com.acme.workforce.identity.application;

import com.acme.workforce.common.application.DomainException;
import org.springframework.security.oauth2.jwt.Jwt;

public final class VerifiedIdentity {
 private VerifiedIdentity() {}
 public static boolean emailVerified(Jwt jwt) {
  return Boolean.TRUE.equals(jwt.getClaims().get("email_verified"));
 }
 public static void requireVerifiedEmail(Jwt jwt) {
  if(!emailVerified(jwt)) throw new DomainException(403,"EMAIL_VERIFICATION_REQUIRED","Verify your email with the identity provider, then sign in again.");
 }
}
