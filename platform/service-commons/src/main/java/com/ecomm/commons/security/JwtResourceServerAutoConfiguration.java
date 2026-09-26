package com.ecomm.commons.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Makes every service an OAuth2 resource server that accepts only Keycloak-issued JWTs.
 *
 * <ul>
 *   <li>Keys come from the configured JWK set URI; the {@code iss} claim is checked explicitly
 *       against the configured issuer (see {@link JwtResourceServerProperties}).
 *   <li>{@code /actuator/health} is open; every other request needs a valid token. Restrict by role
 *       with {@code @PreAuthorize("hasRole('STAFF')")}.
 *   <li>Realm roles become authorities via {@link KeycloakRealmRoleConverter}.
 *   <li>A {@link CurrentCustomer} controller parameter resolves to the token's {@code sub}.
 *   <li>401 and 403 come back as problem details.
 * </ul>
 *
 * A service that needs different URL rules can declare its own {@link SecurityFilterChain}.
 */
@AutoConfiguration(
    beforeName = {
      "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration",
      "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
      "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
      "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
    })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, JwtDecoder.class})
@EnableConfigurationProperties(JwtResourceServerProperties.class)
@EnableMethodSecurity
@Import(SecurityProblemDetailHandler.class)
public class JwtResourceServerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  JwtDecoder jwtDecoder(JwtResourceServerProperties properties) {
    var decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUriOrDefault()).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuerUri()));
    return decoder;
  }

  @Bean
  @ConditionalOnMissingBean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
      throws Exception {
    AuthenticationEntryPoint entryPoint = problemDetailEntryPoint(exceptionResolver);
    AccessDeniedHandler accessDeniedHandler = problemDetailAccessDeniedHandler(exceptionResolver);

    var authenticationConverter = new JwtAuthenticationConverter();
    authenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());

    return http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
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
        .csrf(csrf -> csrf.disable())
        .build();
  }

  @Bean
  WebMvcConfigurer currentCustomerWebMvcConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentCustomerArgumentResolver());
      }
    };
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
