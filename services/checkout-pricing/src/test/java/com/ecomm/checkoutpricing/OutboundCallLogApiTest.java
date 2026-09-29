package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Each call Checkout makes to another service leaves one line in its log, failures included. */
@ExtendWith(OutputCaptureExtension.class)
class OutboundCallLogApiTest extends CheckoutApiTest {

  @Test
  void everyDownstreamCallIsLoggedWithItsStatus(CapturedOutput output) {
    stubSuccessfulCheckout();

    checkout().expectStatus().isOk();

    assertThat(output)
        .contains("GET " + DOWNSTREAM.baseUrl() + "/cart -> 200")
        .contains("GET " + DOWNSTREAM.baseUrl() + "/variants/PHN-PIXEL-9 -> 200")
        .contains("POST " + DOWNSTREAM.baseUrl() + "/orders -> 201")
        .contains("POST " + DOWNSTREAM.baseUrl() + "/stock/decrement -> 200")
        .contains("POST " + DOWNSTREAM.baseUrl() + "/payments -> 201")
        .contains("PATCH " + DOWNSTREAM.baseUrl() + "/orders/" + ORDER_ID + "/status -> 200")
        .contains("DELETE " + DOWNSTREAM.baseUrl() + "/cart -> 204");
  }

  @Test
  void aRefusedCallIsLoggedWithItsStatus(CapturedOutput output) {
    stubSuccessfulCheckout();
    stubDecrement(aResponse().withStatus(409));

    checkout();

    assertThat(output).contains("POST " + DOWNSTREAM.baseUrl() + "/stock/decrement -> 409");
  }
}
