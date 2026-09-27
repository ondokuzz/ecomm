package com.ecomm.checkoutpricing.adapter.out.http;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * The {@link RestClient}s behind the outbound ports.
 *
 * <p>Cart calls carry the token of the Customer making the current request, and Catalog reads are
 * public. Inventory, Order Management and Payment get clients that carry Checkout's own token from
 * the {@code checkout} client-credentials registration (Identity & Access ADR 0002). That token is
 * cached in memory, replaced once it has less than a minute left, and replaced on a 401 before the
 * one retry.
 */
@Configuration
@EnableConfigurationProperties(DownstreamProperties.class)
class HttpClientsConfiguration {

  static final String REGISTRATION = "checkout";

  /**
   * Checkout's own identity. The token belongs to the service, not to whichever Customer's request
   * needs it, so it is cached under this one principal.
   */
  private static final Authentication CHECKOUT =
      UsernamePasswordAuthenticationToken.unauthenticated("checkout", null);

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  OAuth2AuthorizedClientManager checkoutAuthorizedClientManager(
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientService authorizedClients,
      Clock clock) {
    // The default clock skew is 60 seconds: a token with less left is replaced before it is sent.
    var provider = new ClientCredentialsOAuth2AuthorizedClientProvider();
    provider.setClock(clock);
    var manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
    manager.setAuthorizedClientProvider(provider);
    return manager;
  }

  @Bean
  RestClient cartRestClient(RestClient.Builder builder, DownstreamProperties urls) {
    return builder
        .clone()
        .baseUrl(urls.cartUrl())
        .requestInterceptor(new ForwardCustomerToken())
        .build();
  }

  @Bean
  RestClient catalogRestClient(RestClient.Builder builder, DownstreamProperties urls) {
    return builder.clone().baseUrl(urls.catalogUrl()).build();
  }

  @Bean
  RestClient inventoryRestClient(
      RestClient.Builder builder,
      DownstreamProperties urls,
      @Qualifier("checkoutAuthorizedClientManager") OAuth2AuthorizedClientManager manager,
      OAuth2AuthorizedClientService authorizedClients) {
    return internal(builder, urls.inventoryUrl(), manager, authorizedClients);
  }

  @Bean
  RestClient orderManagementRestClient(
      RestClient.Builder builder,
      DownstreamProperties urls,
      @Qualifier("checkoutAuthorizedClientManager") OAuth2AuthorizedClientManager manager,
      OAuth2AuthorizedClientService authorizedClients) {
    return internal(builder, urls.orderManagementUrl(), manager, authorizedClients);
  }

  @Bean
  RestClient paymentRestClient(
      RestClient.Builder builder,
      DownstreamProperties urls,
      @Qualifier("checkoutAuthorizedClientManager") OAuth2AuthorizedClientManager manager,
      OAuth2AuthorizedClientService authorizedClients) {
    return internal(builder, urls.paymentUrl(), manager, authorizedClients);
  }

  /** A client that sends Checkout's token, retrying once with a fresh one on a 401. */
  private static RestClient internal(
      RestClient.Builder builder,
      String baseUrl,
      OAuth2AuthorizedClientManager manager,
      OAuth2AuthorizedClientService authorizedClients) {
    var oauth2 = new OAuth2ClientHttpRequestInterceptor(manager);
    oauth2.setClientRegistrationIdResolver(request -> REGISTRATION);
    oauth2.setPrincipalResolver(request -> CHECKOUT);
    // On a 401, drop the cached token so the retry below fetches a new one.
    oauth2.setAuthorizationFailureHandler(
        OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(authorizedClients));
    return builder
        .clone()
        .baseUrl(baseUrl)
        // The first interceptor wraps the rest, so the retry re-runs the OAuth2 one.
        .requestInterceptor(new RetryOnceOnUnauthorized())
        .requestInterceptor(oauth2)
        .build();
  }
}
