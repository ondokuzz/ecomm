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
import java.util.List;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Test-only: Fulfillment & Shipping's Warehouse read ({@code GET /warehouses/{id}}) in WireMock, as
 * the {@code ReturnAddresses} adapter calls it. {@code WH-BER} is configured; any other Warehouse
 * is a 404, as the service answers for one it doesn't have.
 */
final class FulfillmentShipping {

  static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());
  static final String BERLIN =
      """
      {"id": "WH-BER", "name": "Berlin",
       "returnAddress": {"name": "Ecomm Returns Berlin", "street": "Lagerstraße 1",
                         "postcode": "10115", "city": "Berlin", "country": "DE"}}
      """;

  static {
    SERVER.start();
    reset();
  }

  private FulfillmentShipping() {}

  static void registerWith(DynamicPropertyRegistry registry) {
    registry.add("ecomm.notifications.fulfillment-shipping-url", SERVER::baseUrl);
  }

  /** Forgets every request, and serves {@code WH-BER} again. */
  static void reset() {
    SERVER.resetAll();
    SERVER.stubFor(get(urlPathMatching("/warehouses/.*")).atPriority(10).willReturn(notFound()));
    SERVER.stubFor(get(urlEqualTo("/warehouses/WH-BER")).willReturn(okJson(BERLIN)));
  }

  /** The Warehouse read failing {@code times} times with a 503, then answering again. */
  static void failing(int times) {
    var state = Scenario.STARTED;
    for (var i = 1; i <= times; i++) {
      var next = "failed " + i;
      SERVER.stubFor(
          get(urlEqualTo("/warehouses/WH-BER"))
              .atPriority(1)
              .inScenario("failing")
              .whenScenarioStateIs(state)
              .willReturn(aResponse().withStatus(503))
              .willSetStateTo(next));
      state = next;
    }
    SERVER.stubFor(
        get(urlEqualTo("/warehouses/WH-BER"))
            .atPriority(1)
            .inScenario("failing")
            .whenScenarioStateIs(state)
            .willReturn(okJson(BERLIN)));
  }

  static List<LoggedRequest> warehouseReads() {
    return SERVER.findAll(getRequestedFor(urlPathMatching("/warehouses/.*")));
  }
}
