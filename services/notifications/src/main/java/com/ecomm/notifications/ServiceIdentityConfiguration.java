package com.ecomm.notifications;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;

/**
 * Notifications' own identity for its calls to other services: a client-credentials token from the
 * {@code notifications} client (Identity & Access ADR 0002), cached until it has less than a minute
 * left and dropped on a 401. Its service account holds realm-management's {@code view-users}, for
 * Keycloak's admin API, and the {@code NOTIFICATIONS} realm role, for Fulfillment & Shipping's
 * Warehouse read.
 */
@Configuration
class ServiceIdentityConfiguration {

  static final String REGISTRATION = "notifications";

  private static final UsernamePasswordAuthenticationToken NOTIFICATIONS =
      UsernamePasswordAuthenticationToken.unauthenticated(REGISTRATION, null);

  @Bean
  OAuth2ClientHttpRequestInterceptor serviceIdentity(
      ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
    var manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
    manager.setAuthorizedClientProvider(new ClientCredentialsOAuth2AuthorizedClientProvider());
    var oauth2 = new OAuth2ClientHttpRequestInterceptor(manager);
    oauth2.setClientRegistrationIdResolver(request -> REGISTRATION);
    oauth2.setPrincipalResolver(request -> NOTIFICATIONS);
    oauth2.setAuthorizationFailureHandler(
        OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(authorizedClients));
    return oauth2;
  }
}
