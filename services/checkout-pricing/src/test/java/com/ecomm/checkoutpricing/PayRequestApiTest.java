package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** Paying needs a Payment method: a request without a usable one is a 400 and starts nothing. */
class PayRequestApiTest extends CheckoutApiTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "{}",
        "{\"paymentMethod\": null}",
        "{\"paymentMethod\": \" \"}",
        "{\"paymentMethod\": 42}",
        "{\"paymentMethod\": {\"token\": \"tok_approve\"}}",
        "not json"
      })
  void payingWithoutAPaymentMethodIsABadRequestAndStartsNothing(String body) {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    DOWNSTREAM.resetRequests();

    payWith(customerToken(), sessionId, body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
    assertThat(saga.started()).isEmpty();
    currentSession().expectStatus().isOk();
  }

  @Test
  void aPaymentMethodPast255CharactersIsABadRequestAndStartsNothing() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    payWithMethod(sessionId, "tok_" + "x".repeat(252)).expectStatus().isBadRequest();

    assertThat(saga.started()).isEmpty();
  }
}
