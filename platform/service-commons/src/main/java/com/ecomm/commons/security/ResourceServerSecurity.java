package com.ecomm.commons.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Everything a security filter chain needs to accept Keycloak-issued JWTs, except its URL rules:
 * bearer-token authentication with realm roles as authorities, 401 and 403 as problem details, no
 * session and no CSRF. The default chain in {@link JwtResourceServerAutoConfiguration} adds its
 * rules to this; so does any module that declares its own chain.
 */
public final class ResourceServerSecurity {

  private ResourceServerSecurity() {}

  /**
   * Configures {@code http} as a stateless resource server. {@code exceptionResolver} is the MVC
   * {@code handlerExceptionResolver}, which renders the 401 and 403 bodies.
   */
  public static HttpSecurity configure(
      HttpSecurity http, HandlerExceptionResolver exceptionResolver) throws Exception {
    AuthenticationEntryPoint entryPoint = problemDetailEntryPoint(exceptionResolver);
    AccessDeniedHandler accessDeniedHandler = problemDetailAccessDeniedHandler(exceptionResolver);

    var authenticationConverter = new JwtAuthenticationConverter();
    authenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());

    return http.oauth2ResourceServer(
            server ->
                server
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable());
  }

  /** Keeps the {@code WWW-Authenticate} header, then renders the body via the MVC handlers. */
  private static AuthenticationEntryPoint problemDetailEntryPoint(
      HandlerExceptionResolver exceptionResolver) {
    var bearer = new BearerTokenAuthenticationEntryPoint();
    return (request, response, e) -> {
      bearer.commence(request, response, e);
      exceptionResolver.resolveException(request, response, null, e);
    };
  }

  private static AccessDeniedHandler problemDetailAccessDeniedHandler(
      HandlerExceptionResolver exceptionResolver) {
    var bearer = new BearerTokenAccessDeniedHandler();
    return (request, response, e) -> {
      bearer.handle(request, response, e);
      exceptionResolver.resolveException(request, response, null, e);
    };
  }
}
