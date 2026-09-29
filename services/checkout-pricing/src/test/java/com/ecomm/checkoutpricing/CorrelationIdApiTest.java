package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** One checkout reads as one story across services: every downstream call names its request. */
class CorrelationIdApiTest extends CheckoutApiTest {

  private static final String HEADER = "X-Correlation-Id";

  @Test
  void everyDownstreamCallCarriesTheIncomingCorrelationId() {
    stubSuccessfulCheckout();

    http.post()
        .uri("/checkout")
        .headers(
            h -> {
              h.setBearerAuth(customerToken());
              h.set(HEADER, "checkout-7f3a-42");
            })
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HEADER, "checkout-7f3a-42");

    assertThat(downstreamCalls())
        .isNotEmpty()
        .allSatisfy(call -> assertThat(call.getHeader(HEADER)).isEqualTo("checkout-7f3a-42"));
  }

  @Test
  void everyDownstreamCallCarriesTheGeneratedCorrelationId() {
    stubSuccessfulCheckout();

    var generated =
        checkout()
            .expectStatus()
            .isOk()
            .returnResult(String.class)
            .getResponseHeaders()
            .getFirst(HEADER);

    assertThat(generated).isNotBlank();
    assertThat(downstreamCalls())
        .isNotEmpty()
        .allSatisfy(call -> assertThat(call.getHeader(HEADER)).isEqualTo(generated));
  }

  /** Every call to Cart, Catalog, Inventory, Order Management and Payment, not to Keycloak. */
  private static List<LoggedRequest> downstreamCalls() {
    return DOWNSTREAM.findAll(anyRequestedFor(anyUrl())).stream()
        .filter(call -> !call.getUrl().equals(TOKEN_PATH))
        .toList();
  }
}
