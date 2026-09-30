package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * A declined payment cancels the Order and is a 402 naming the gateway's reason; a gateway that
 * fails to answer does the same with a 502. Either way the Checkout Session and its Reservation
 * stay, so the Customer can pay it again, with another payment method.
 */
class DeclinedPaymentApiTest extends CheckoutApiTest {

  @Test
  void aDeclineCancelsTheOrderAndIsPaymentRequiredWithItsReason() {
    stubSuccessfulCheckout();
    stubDeclinedPayment("insufficient_funds");

    payWithMethod(startedSessionId(), "tok_insufficient_funds")
        .expectStatus()
        .isEqualTo(402)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.declineReason")
        .isEqualTo("insufficient_funds");

    verifyCancelledAndSessionKept();
  }

  @Test
  void theSessionCanBePaidAgainAfterADecline() {
    stubSuccessfulCheckout();
    stubDeclinedPayment("card_declined");
    var sessionId = startedSessionId();
    payWithMethod(sessionId, "tok_decline").expectStatus().isEqualTo(402);

    stubPayment();
    payWithMethod(sessionId, APPROVE)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.orderId")
        .isEqualTo(ORDER_ID)
        .jsonPath("$.status")
        .isEqualTo("PAID");

    DOWNSTREAM.verify(
        1, postRequestedFor(urlEqualTo("/reservations/" + RESERVATION_ID + "/commit")));
    DOWNSTREAM.verify(1, deleteRequestedFor(urlEqualTo("/cart")));
    currentSession().expectStatus().isNotFound();
  }

  @Test
  void aGatewayFailureCancelsTheOrderAndIsABadGateway() {
    stubSuccessfulCheckout();
    stubPayment(
        aResponse()
            .withStatus(502)
            .withHeader("Content-Type", "application/problem+json")
            .withBody("{\"status\": 502}"));

    payWithMethod(startedSessionId(), "tok_gateway_error")
        .expectStatus()
        .isEqualTo(502)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    verifyCancelledAndSessionKept();
  }

  @Test
  void theSessionCanBePaidAgainAfterAGatewayFailure() {
    stubSuccessfulCheckout();
    stubPayment(aResponse().withStatus(502));
    var sessionId = startedSessionId();
    payWithMethod(sessionId, "tok_gateway_error").expectStatus().isEqualTo(502);

    stubPayment();
    payWithMethod(sessionId, APPROVE).expectStatus().isOk();
  }

  @Test
  void aPaymentInAStatusCheckoutDoesntKnowIsABadGateway() {
    stubSuccessfulCheckout();
    stubPayment(
        aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"id\": \"p-3\", \"status\": \"PENDING\"}"));

    checkout().expectStatus().isEqualTo(502);

    verifyCancelledAndSessionKept();
  }

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
  void payingWithoutAPaymentMethodIsABadRequestAndDoesNothing(String body) {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    DOWNSTREAM.resetRequests();

    payWith(customerToken(), sessionId, body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
    currentSession().expectStatus().isOk();
  }

  @Test
  void aPaymentMethodPast255CharactersIsABadRequest() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    DOWNSTREAM.resetRequests();

    payWithMethod(sessionId, "tok_" + "x".repeat(252)).expectStatus().isBadRequest();

    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
  }

  /**
   * The Order is cancelled for its Customer and nothing more happens: the Reservation is neither
   * committed nor released, and the Cart and the session stay.
   */
  private void verifyCancelledAndSessionKept() {
    DOWNSTREAM.verify(
        patchRequestedFor(urlEqualTo("/orders/" + ORDER_ID + "/status"))
            .withRequestBody(matchingJsonPath("$.status", equalTo("CANCELLED")))
            .withRequestBody(matchingJsonPath("$.customerId", equalTo(CUSTOMER_ID))));
    DOWNSTREAM.verify(
        0,
        patchRequestedFor(urlEqualTo("/orders/" + ORDER_ID + "/status"))
            .withRequestBody(matchingJsonPath("$.status", equalTo("PAID"))));
    DOWNSTREAM.verify(0, postRequestedFor(urlPathMatching("/reservations/.*/(commit|release)")));
    DOWNSTREAM.verify(0, deleteRequestedFor(urlEqualTo("/cart")));
    currentSession().expectStatus().isOk();
  }
}
