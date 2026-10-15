package com.ecomm.orchestration.adapter.out.http;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient}s the Saga steps call other contexts with. Every one carries
 * Orchestration's own token from the {@code orchestration} client-credentials registration
 * (Identity & Access ADR 0002), as Checkout's internal clients carry Checkout's. That token is
 * cached in memory, replaced once it has less than a minute left, and replaced on a 401 before the
 * one retry.
 */
@Component
public class ServiceRestClients {

  static final String REGISTRATION = "orchestration";

  /** Orchestration's own identity: no Customer is behind a Saga step, so one token serves all. */
  private static final Authentication ORCHESTRATION =
      UsernamePasswordAuthenticationToken.unauthenticated(REGISTRATION, null);

  private final RestClient.Builder builder;
  private final OAuth2ClientHttpRequestInterceptor oauth2;
  private final JdkClientHttpRequestFactory requests;

  ServiceRestClients(
      RestClient.Builder builder,
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientService authorizedClients,
      Clock clock,
      @Value("${ecomm.orchestration.connect-timeout}") Duration connectTimeout,
      @Value("${ecomm.orchestration.read-timeout}") Duration readTimeout) {
    // The default clock skew is 60 seconds: a token with less left is replaced before it is sent.
    var provider = new ClientCredentialsOAuth2AuthorizedClientProvider();
    provider.setClock(clock);
    var manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
    manager.setAuthorizedClientProvider(provider);

    oauth2 = new OAuth2ClientHttpRequestInterceptor(manager);
    oauth2.setClientRegistrationIdResolver(request -> REGISTRATION);
    oauth2.setPrincipalResolver(request -> ORCHESTRATION);
    // On a 401, drop the cached token so the retry fetches a new one.
    oauth2.setAuthorizationFailureHandler(
        OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(authorizedClients));
    this.builder = builder;
    // A call gives up before Temporal's budget for the attempt (CheckoutSaga.ATTEMPT) runs out, so
    // the attempt fails here, where its retry is counted and replays with its key, instead of being
    // abandoned while its call still waits.
    requests =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(connectTimeout).build());
    requests.setReadTimeout(readTimeout);
  }

  /** A client for the service at {@code baseUrl} that sends Orchestration's token. */
  public RestClient forService(String baseUrl) {
    return builder
        .clone()
        .baseUrl(baseUrl)
        .requestFactory(requests)
        // The first interceptor wraps the rest, so the retry re-runs the OAuth2 one.
        .requestInterceptor(new RetryOnceOnUnauthorized())
        .requestInterceptor(oauth2)
        .build();
  }
}
