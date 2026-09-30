package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * A step of paying that fails after the Order exists cancels it, and leaves the Cart and the
 * Checkout Session as they were. A Stock commit failing after the Payment is authorized is no
 * exception: the Payment stays authorized, a known limitation until the Sagas.
 */
class PayFailureApiTest extends CheckoutApiTest {

  @Test
  void aCommitFailingAfterAuthorizationCancelsTheOrderAndKeepsTheCart() {
    stubSuccessfulCheckout();
    stubCommit(
        aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/problem+json")
            .withBody(
                """
                {"status": 409, "reservationExpired": "%s"}
                """
                    .formatted(RESERVATION_ID)));

    checkout().expectStatus().isEqualTo(502);

    DOWNSTREAM.verify(postRequestedFor(urlEqualTo("/payments")));
    verifyCancelledAndCartKept();
    DOWNSTREAM.verify(
        0,
        patchRequestedFor(urlEqualTo("/orders/" + ORDER_ID + "/status")).withRequestBody(paid()));
  }

  @Test
  void anInventoryFailureCancelsTheOrderAndKeepsTheCart() {
    stubSuccessfulCheckout();
    stubCommit(serverError());

    checkout().expectStatus().isEqualTo(502);

    verifyCancelledAndCartKept();
  }

  @Test
  void aPaymentFailureCancelsTheOrderAndKeepsTheCart() {
    stubSuccessfulCheckout();
    stubPayment(aResponse().withStatus(400));

    checkout()
        .expectStatus()
        .isEqualTo(502)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    verifyCancelledAndCartKept();
    DOWNSTREAM.verify(
        0,
        patchRequestedFor(urlEqualTo("/orders/" + ORDER_ID + "/status")).withRequestBody(paid()));
    DOWNSTREAM.verify(0, postRequestedFor(urlPathMatching("/reservations/.*/commit")));
  }

  @Test
  void anOrderThatCantBeMarkedPaidIsCancelledAndTheCartKept() {
    stubSuccessfulCheckout();
    DOWNSTREAM.stubFor(statusChange().withRequestBody(paid()).willReturn(serverError()));

    checkout().expectStatus().isEqualTo(502);

    verifyCancelledAndCartKept();
  }

  @Test
  void aCartThatCantBeClearedStillLeavesThePaidOrder() {
    stubSuccessfulCheckout();
    DOWNSTREAM.stubFor(delete("/cart").willReturn(serverError()));

    checkout().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("PAID");

    currentSession().expectStatus().isNotFound();
    DOWNSTREAM.verify(
        0,
        patchRequestedFor(urlEqualTo("/orders/" + ORDER_ID + "/status"))
            .withRequestBody(cancelled()));
  }

  /** The Order is cancelled for its Customer; the Cart and the Checkout Session stay. */
  private void verifyCancelledAndCartKept() {
    DOWNSTREAM.verify(
        patchRequestedFor(urlEqualTo("/orders/" + ORDER_ID + "/status"))
            .withRequestBody(cancelled())
            .withRequestBody(matchingJsonPath("$.customerId", equalTo(CUSTOMER_ID))));
    DOWNSTREAM.verify(0, deleteRequestedFor(urlEqualTo("/cart")));
    currentSession().expectStatus().isOk();
  }

  private static StringValuePattern cancelled() {
    return matchingJsonPath("$.status", equalTo("CANCELLED"));
  }

  private static StringValuePattern paid() {
    return matchingJsonPath("$.status", equalTo("PAID"));
  }
}
