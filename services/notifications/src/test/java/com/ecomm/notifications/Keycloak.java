package com.ecomm.notifications;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only: Keycloak's token endpoint and the admin API's user read, in WireMock, as the {@code
 * CustomerDirectory} calls them. A Customer is known once {@link #customer} stubs them; any other
 * is a 404, as Keycloak answers for a user it doesn't have.
 */
final class Keycloak {

  static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());
  static final String TOKEN_PATH = "/realms/ecomm/protocol/openid-connect/token";
  static final String USERS_PATH = "/admin/realms/ecomm/users/";

  private static final JsonMapper JSON = new JsonMapper();

  static {
    SERVER.start();
    reset();
  }

  private Keycloak() {}

  static void registerWith(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.oauth2.client.registration.notifications.client-secret", () -> "test");
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.token-uri",
        () -> SERVER.baseUrl() + TOKEN_PATH);
    registry.add(
        "ecomm.notifications.keycloak.admin-url", () -> SERVER.baseUrl() + "/admin/realms/ecomm");
  }

  /**
   * Forgets every Customer and every request, and serves tokens again. Each lives 60 seconds, which
   * the client's minute of clock skew counts as expiring, so every call fetches one: no test gets a
   * token an earlier one was given.
   */
  static void reset() {
    SERVER.resetAll();
    SERVER.stubFor(
        post(urlEqualTo(TOKEN_PATH))
            .willReturn(
                okJson(
                    """
                    {"access_token": "notifications-token-1", "token_type": "Bearer", "expires_in": 60}
                    """)));
    SERVER.stubFor(get(urlPathMatching(USERS_PATH + ".*")).atPriority(10).willReturn(notFound()));
  }

  /** A Customer Keycloak knows, with an email address unless it is {@code null}. */
  static void customer(String id, String email, String givenName, String familyName) {
    var user = new LinkedHashMap<String, Object>();
    user.put("id", id);
    user.put("username", email == null ? id : email);
    if (email != null) {
      user.put("email", email);
    }
    user.put("firstName", givenName);
    user.put("lastName", familyName);
    user.put("enabled", true);
    user.put("attributes", Map.of("locale", List.of("en")));
    SERVER.stubFor(
        get(urlEqualTo(USERS_PATH + id)).willReturn(okJson(JSON.writeValueAsString(user))));
  }

  /** Keycloak's admin API failing for every user, as when Keycloak is down behind a proxy. */
  static void down() {
    SERVER.stubFor(
        get(urlPathMatching(USERS_PATH + ".*"))
            .atPriority(1)
            .willReturn(aResponse().withStatus(503)));
  }
}
