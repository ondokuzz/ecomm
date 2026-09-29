package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * A Cart that can't be checked out is refused before any Order exists. A step that fails after the
 * Order exists cancels it and leaves the Cart as it was.
 */
class CheckoutFailureApiTest extends CheckoutApiTest {

  @Test
  void anEmptyCartIsABadRequest() {
    stubSuccessfulCheckout();
    stubCart("");

    checkout()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo("/orders")));
  }

  @Test
  void aProductMissingFromTheCatalogIsAConflictNamingIt() {
    stubSuccessfulCheckout();
    stubCart(
        """
        {"variantId": "PHN-PIXEL-9", "quantity": 1}, {"variantId": "PHN-GONE", "quantity": 1}
        """);
    DOWNSTREAM.stubFor(get("/variants/PHN-GONE").willReturn(notFound()));

    checkout()
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.unknownVariants[0]")
        .isEqualTo("PHN-GONE")
        .jsonPath("$.unknownVariants.length()")
        .isEqualTo(1);

    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo("/orders")));
  }

  @Test
  void anOutOfStockVariantIsAConflictNamingIt() {
    stubSuccessfulCheckout();
    stubDecrement(
        aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/problem+json")
            .withBody(
                """
                {"status": 409, "detail": "Not enough stock", "insufficientStock": ["PHN-PIXEL-9"]}
                """));

    checkout()
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.outOfStock[0]")
        .isEqualTo("PHN-PIXEL-9");
  }

  @Test
  void anOutOfStockVariantCancelsTheOrderAndKeepsTheCart() {
    stubSuccessfulCheckout();
    stubDecrement(aResponse().withStatus(409));

    checkout().expectStatus().isEqualTo(409);

    verifyCancelledAndCartKept();
    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo("/payments")));
  }

  @Test
  void anInventoryFailureCancelsTheOrderAndKeepsTheCart() {
    stubSuccessfulCheckout();
    stubDecrement(serverError());

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

    DOWNSTREAM.verify(
        0,
        patchRequestedFor(urlEqualTo("/orders/" + ORDER_ID + "/status"))
            .withRequestBody(cancelled()));
  }

  private static void verifyCancelledAndCartKept() {
    DOWNSTREAM.verify(
        patchRequestedFor(urlEqualTo("/orders/" + ORDER_ID + "/status"))
            .withRequestBody(cancelled())
            .withRequestBody(matchingJsonPath("$.customerId", equalTo(CUSTOMER_ID))));
    DOWNSTREAM.verify(0, deleteRequestedFor(urlEqualTo("/cart")));
  }

  private static StringValuePattern cancelled() {
    return matchingJsonPath("$.status", equalTo("CANCELLED"));
  }

  private static StringValuePattern paid() {
    return matchingJsonPath("$.status", equalTo("PAID"));
  }
}
