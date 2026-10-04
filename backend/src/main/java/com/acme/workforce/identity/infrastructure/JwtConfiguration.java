package com.acme.workforce.identity.infrastructure;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtConfiguration {
    @Bean
    OAuth2TokenValidator<Jwt> applicationTokenValidator(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.audiences}") String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        values -> values != null && values.contains(audience)),
                new JwtClaimValidator<String>(JwtClaimNames.SUB,
                        subject -> subject != null && !subject.isBlank()));
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwks,
            OAuth2TokenValidator<Jwt> applicationTokenValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(applicationTokenValidator);
        return decoder;
    }
}
