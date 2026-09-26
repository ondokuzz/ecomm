package com.ecomm.commons.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where a service finds Keycloak's signing keys and which issuer it trusts.
 *
 * <p>The two differ inside Docker Compose: tokens carry the browser-facing issuer ({@code
 * http://localhost:8180/realms/ecomm}), while services fetch keys over the internal network ({@code
 * http://keycloak:8080/...}). When {@code jwk-set-uri} is unset, it is derived from the issuer.
 */
@ConfigurationProperties("ecomm.security.jwt")
public record JwtResourceServerProperties(
    @DefaultValue("http://localhost:8180/realms/ecomm") String issuerUri, String jwkSetUri) {

  public String jwkSetUriOrDefault() {
    return jwkSetUri != null ? jwkSetUri : issuerUri + "/protocol/openid-connect/certs";
  }
}
