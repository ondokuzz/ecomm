package com.ecomm.notifications.adapter.out.keycloak;

import com.ecomm.notifications.application.port.out.CustomerDirectory;
import com.ecomm.notifications.domain.Recipient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Reads Customers from Keycloak's admin API ({@code GET /users/{id}}) with Notifications' own
 * client-credentials token, from the {@code notifications} client, whose service account holds
 * realm-management's {@code view-users} (Identity & Access ADR 0002). Answers, a Customer Keycloak
 * doesn't know included, are cached for {@code ecomm.notifications.keycloak.cache-for} (10
 * minutes), so a burst of a Customer's messages reads them once. Anything but a user or a 404 is
 * the directory being unavailable.
 */
@Component
class KeycloakCustomerDirectory implements CustomerDirectory {

  /** The fields of Keycloak's UserRepresentation Notifications reads. */
  record User(
      String email, String firstName, String lastName, Map<String, List<String>> attributes) {}

  private final RestClient keycloak;
  private final Cache<String, Optional<Recipient>> cache;

  KeycloakCustomerDirectory(
      RestClient.Builder builder,
      OAuth2ClientHttpRequestInterceptor serviceIdentity,
      @Value("${ecomm.notifications.keycloak.admin-url}") String adminUrl,
      @Value("${ecomm.notifications.keycloak.cache-for}") Duration cacheFor,
      @Value("${ecomm.notifications.keycloak.timeout}") Duration timeout) {
    var requests =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    requests.setReadTimeout(timeout);
    keycloak =
        builder
            .clone()
            .baseUrl(adminUrl)
            .requestFactory(requests)
            .requestInterceptor(serviceIdentity)
            .build();
    cache = Caffeine.newBuilder().expireAfterWrite(cacheFor).maximumSize(10_000).build();
  }

  @Override
  public Optional<Recipient> find(String customerId) {
    var cached = cache.getIfPresent(customerId);
    if (cached != null) {
      return cached;
    }
    var found = read(customerId);
    cache.put(customerId, found);
    return found;
  }

  private Optional<Recipient> read(String customerId) {
    User user;
    try {
      user = keycloak.get().uri("/users/{id}", customerId).retrieve().body(User.class);
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return Optional.empty();
      }
      throw new DirectoryUnavailableException("Keycloak answered " + e.getStatusCode(), e);
    } catch (RuntimeException e) {
      // No answer, a 5xx, or no token to ask with: Spring Security's own exception.
      throw new DirectoryUnavailableException("Keycloak didn't answer: " + e.getMessage(), e);
    }
    if (user == null || user.email() == null || user.email().isBlank()) {
      return Optional.empty();
    }
    return Optional.of(
        new Recipient(customerId, user.email(), user.firstName(), user.lastName(), localeOf(user)));
  }

  private static Locale localeOf(User user) {
    var locales = user.attributes() == null ? null : user.attributes().get("locale");
    return locales == null || locales.isEmpty()
        ? Locale.ENGLISH
        : Locale.forLanguageTag(locales.getFirst());
  }
}
