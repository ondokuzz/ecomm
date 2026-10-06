package com.ecomm.commons.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Stands in for Keycloak in tests. It serves a JWK set over HTTP and mints tokens shaped like the
 * {@code ecomm} realm's, so a service's real JWT validation runs against them. Point the service at
 * it with:
 *
 * <pre>{@code
 * @DynamicPropertySource
 * static void keycloak(DynamicPropertyRegistry registry) {
 *   FakeKeycloak.registerWith(registry);
 * }
 * }</pre>
 */
public final class FakeKeycloak {

  public static final String ISSUER = "http://localhost:8180/realms/ecomm";

  private static final RSAKey REALM_KEY = generateKey();
  private static final JwtEncoder REALM_SIGNER = signerFor(REALM_KEY);
  private static final String JWK_SET_URI = serveJwkSet(new JWKSet(REALM_KEY.toPublicJWK()));

  private FakeKeycloak() {}

  public static void registerWith(DynamicPropertyRegistry registry) {
    registry.add("ecomm.security.jwt.issuer-uri", () -> ISSUER);
    registry.add("ecomm.security.jwt.jwk-set-uri", () -> JWK_SET_URI);
  }

  /** A valid access token for {@code subject}, carrying the given realm roles. */
  public static String token(String subject, String... roles) {
    return mint(REALM_SIGNER, ISSUER, subject, Map.of(), roles);
  }

  /**
   * A valid access token for {@code subject} that also carries {@code claims}, such as the {@code
   * given_name} and {@code family_name} the realm's {@code profile} scope adds.
   */
  public static String tokenWithClaims(
      String subject, Map<String, Object> claims, String... roles) {
    return mint(REALM_SIGNER, ISSUER, subject, claims, roles);
  }

  /** A token signed by the realm's key but naming a different issuer. */
  public static String tokenFromIssuer(String issuer, String subject) {
    return mint(REALM_SIGNER, issuer, subject, Map.of(), "CUSTOMER");
  }

  /** A token with the right issuer, signed by a key the realm never published. */
  public static String tokenSignedByUnknownKey(String subject) {
    return mint(signerFor(generateKey()), ISSUER, subject, Map.of(), "CUSTOMER");
  }

  private static String mint(
      JwtEncoder signer,
      String issuer,
      String subject,
      Map<String, Object> extraClaims,
      String... roles) {
    var now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .claims(c -> c.putAll(extraClaims))
            .issuer(issuer)
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .claim("realm_access", Map.of("roles", List.of(roles)))
            .build();
    var header = JwsHeader.with(() -> "RS256").build();
    return signer.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private static RSAKey generateKey() {
    try {
      return new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  private static JwtEncoder signerFor(RSAKey key) {
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
  }

  private static String serveJwkSet(JWKSet jwkSet) {
    try {
      var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      var body = jwkSet.toString().getBytes(StandardCharsets.UTF_8);
      server.createContext(
          "/certs",
          exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
              out.write(body);
            }
          });
      server.start();
      return "http://127.0.0.1:" + server.getAddress().getPort() + "/certs";
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
