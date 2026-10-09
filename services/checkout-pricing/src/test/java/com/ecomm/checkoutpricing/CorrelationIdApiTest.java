package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Each step of a checkout reads as one story across services: every downstream call names the
 * request it serves. Paying hands the request's Correlation ID to the checkout Saga, whose calls
 * carry it on (the Temporal adapter's test).
 */
class CorrelationIdApiTest extends CheckoutApiTest {

  private static final String HEADER = "X-Correlation-Id";

  @Test
  void everyCallStartingASessionCarriesTheIncomingCorrelationId() {
    stubSuccessfulCheckout();

    http.post()
        .uri("/checkout/sessions")
        .headers(
            h -> {
              h.setBearerAuth(customerToken());
              h.set(HEADER, "start-7f3a-42");
            })
        .exchange()
        .expectStatus()
        .isCreated()
        .expectHeader()
        .valueEquals(HEADER, "start-7f3a-42");

    assertThat(downstreamCalls())
        .isNotEmpty()
        .allSatisfy(call -> assertThat(call.getHeader(HEADER)).isEqualTo("start-7f3a-42"));
  }

  @Test
  void everyDownstreamCallCarriesTheGeneratedCorrelationId() {
    stubSuccessfulCheckout();

    var generated =
        startSession()
            .expectStatus()
            .isCreated()
            .returnResult(String.class)
            .getResponseHeaders()
            .getFirst(HEADER);

    assertThat(generated).isNotBlank();
    assertThat(downstreamCalls())
        .isNotEmpty()
        .allSatisfy(call -> assertThat(call.getHeader(HEADER)).isEqualTo(generated));
  }

  /** Every call to Cart, Catalog, Promotions and Inventory, not to Keycloak. */
  private static List<LoggedRequest> downstreamCalls() {
    return DOWNSTREAM.findAll(anyRequestedFor(anyUrl())).stream()
        .filter(call -> !call.getUrl().equals(TOKEN_PATH))
        .toList();
  }
}
