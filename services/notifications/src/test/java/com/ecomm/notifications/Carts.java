package com.ecomm.notifications;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only: Cart's internal read of a named Customer's Cart ({@code GET
 * /internal/carts/{customerId}}) in WireMock, as the {@code Carts} adapter calls it. A Customer
 * given no Cart is a 404, as Cart answers for one with none or an empty one.
 */
final class Carts {

  static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());
  private static final JsonMapper JSON = new JsonMapper();

  static {
    SERVER.start();
    reset();
  }

  private Carts() {}

  static void registerWith(DynamicPropertyRegistry registry) {
    registry.add("ecomm.notifications.cart-url", SERVER::baseUrl);
  }

  /** Forgets every request and every Cart. */
  static void reset() {
    SERVER.resetAll();
    SERVER.stubFor(
        get(urlPathMatching("/internal/carts/.*")).atPriority(10).willReturn(notFound()));
  }

  /** The Customer's Cart, last changed at {@code lastChangedAt}, holding {@code items}. */
  static void cart(String customerId, Instant lastChangedAt, Map<String, Integer> items) {
    SERVER.stubFor(
        get(urlEqualTo("/internal/carts/" + customerId))
            .willReturn(okJson(bodyOf(lastChangedAt, items))));
  }

  /** The Customer's Cart read failing {@code times} times with a 503, then answering. */
  static void failingThen(
      int times, String customerId, Instant lastChangedAt, Map<String, Integer> items) {
    var state = Scenario.STARTED;
    for (var i = 1; i <= times; i++) {
      var next = "failed " + i;
      SERVER.stubFor(
          get(urlEqualTo("/internal/carts/" + customerId))
              .inScenario(customerId)
              .whenScenarioStateIs(state)
              .willReturn(aResponse().withStatus(503))
              .willSetStateTo(next));
      state = next;
    }
    SERVER.stubFor(
        get(urlEqualTo("/internal/carts/" + customerId))
            .inScenario(customerId)
            .whenScenarioStateIs(state)
            .willReturn(okJson(bodyOf(lastChangedAt, items))));
  }

  static List<LoggedRequest> readsOf(String customerId) {
    return SERVER.findAll(getRequestedFor(urlEqualTo("/internal/carts/" + customerId)));
  }

  private static String bodyOf(Instant lastChangedAt, Map<String, Integer> items) {
    var body = new LinkedHashMap<String, Object>();
    body.put(
        "items",
        items.entrySet().stream()
            .map(e -> Map.of("variantId", e.getKey(), "quantity", e.getValue()))
            .toList());
    body.put("lastChangedAt", lastChangedAt.toString());
    body.put("version", lastChangedAt.toEpochMilli());
    return JSON.writeValueAsString(body);
  }
}
