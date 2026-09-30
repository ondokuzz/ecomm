package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Paying a Checkout Session places the Order at the session's Prices, authorizes its Payment,
 * commits the Reservation, marks the Order paid, clears the Cart and ends the session. An expired
 * or unknown session does nothing.
 */
class PaySessionApiTest extends CheckoutApiTest {

  @Test
  void payingReturnsThePaidOrder() {
    stubSuccessfulCheckout();

    checkout()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.orderId")
        .isEqualTo(ORDER_ID)
        .jsonPath("$.status")
        .isEqualTo("PAID");
  }

  @Test
  void theStepsRunInOrder() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    DOWNSTREAM.resetRequests();

    pay(sessionId).expectStatus().isOk();

    assertThat(downstreamCalls())
        .containsExactly(
            "POST /orders",
            "POST /payments",
            "POST /reservations/" + RESERVATION_ID + "/commit",
            "PATCH /orders/" + ORDER_ID + "/status",
            "DELETE /cart");
  }

  @Test
  void theOrdersTotalIsAuthorizedWithTheCustomersPaymentMethod() {
    stubSuccessfulCheckout();

    pay(startedSessionId()).expectStatus().isOk();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/payments"))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "customer-42", "orderId": "%s", "paymentMethod": "tok_approve",
                     "amount": {"amountMinor": 159800, "currency": "EUR"}}
                    """
                        .formatted(ORDER_ID))));
  }

  @Test
  void theReservationIsCommittedForTheCustomer() {
    stubSuccessfulCheckout();

    checkout().expectStatus().isOk();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/reservations/" + RESERVATION_ID + "/commit"))
            .withRequestBody(equalToJson("{\"customerId\": \"customer-42\"}")));
  }

  @Test
  void theCartIsClearedAndTheSessionEnds() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    pay(sessionId).expectStatus().isOk();

    DOWNSTREAM.verify(1, deleteRequestedFor(urlEqualTo("/cart")));
    currentSession().expectStatus().isNotFound();
    pay(sessionId).expectStatus().isNotFound();
  }

  @Test
  void theOrderIsPlacedAtThePricesCapturedWhenTheSessionStarted() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    stubVariant("PHN-PIXEL-9", 99900);
    DOWNSTREAM.resetRequests();

    pay(sessionId).expectStatus().isOk();

    DOWNSTREAM.verify(0, getRequestedFor(urlPathMatching("/variants/.*")));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/orders"))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "customer-42", "lines": [
                      {"variantId": "PHN-PIXEL-9", "quantity": 2,
                       "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}
                    ],
                     "tax": {"amountMinor": 0, "currency": "EUR"}}
                    """)));
  }

  @Test
  void theOrderIsPlacedWithTheSessionsDiscount() {
    stubSuccessfulCheckout();
    stubDiscount("WELCOME10", 15980);
    var sessionId = startedSessionId();
    applyCoupon(sessionId, "welcome10").expectStatus().isOk();

    pay(sessionId).expectStatus().isOk();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/orders"))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "customer-42", "lines": [
                      {"variantId": "PHN-PIXEL-9", "quantity": 2,
                       "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}
                    ],
                     "discount": {"couponCode": "WELCOME10",
                                  "amount": {"amountMinor": 15980, "currency": "EUR"}},
                     "tax": {"amountMinor": 0, "currency": "EUR"}}
                    """)));
  }

  @Test
  void theDiscountedTotalIsAuthorized() {
    stubSuccessfulCheckout();
    stubDiscount("WELCOME10", 15980);
    // Order Management's total for those lines, less the discount: 1598.00 - 159.80.
    DOWNSTREAM.stubFor(post("/orders").willReturn(placedOrder(143820)));
    var sessionId = startedSessionId();
    applyCoupon(sessionId, "WELCOME10").expectStatus().isOk();

    pay(sessionId).expectStatus().isOk();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/payments"))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "customer-42", "orderId": "%s", "paymentMethod": "tok_approve",
                     "amount": {"amountMinor": 143820, "currency": "EUR"}}
                    """
                        .formatted(ORDER_ID))));
  }

  @Test
  void aRemovedCouponIsNotSentWithTheOrder() {
    stubSuccessfulCheckout();
    stubDiscount("WELCOME10", 15980);
    var sessionId = startedSessionId();
    applyCoupon(sessionId, "WELCOME10").expectStatus().isOk();
    removeCoupon(sessionId).expectStatus().isOk();

    pay(sessionId).expectStatus().isOk();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/orders"))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "customer-42", "lines": [
                      {"variantId": "PHN-PIXEL-9", "quantity": 2,
                       "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}
                    ],
                     "tax": {"amountMinor": 0, "currency": "EUR"}}
                    """)));
  }

  @Test
  void payingAnExpiredSessionIsGoneAndDoesNothing() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    DOWNSTREAM.resetRequests();

    clock.advance(Duration.ofMinutes(15));

    pay(sessionId)
        .expectStatus()
        .isEqualTo(410)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void aSessionJustShortOfItsExpiryCanStillBePaid() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    clock.advance(Duration.ofMinutes(15).minusSeconds(5));

    pay(sessionId).expectStatus().isOk();
  }

  @Test
  void payingAnUnknownSessionIsNotFoundAndDoesNothing() {
    stubSuccessfulCheckout();

    pay("0b7e6a52-0000-4000-8000-000000000009")
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void anotherCustomersSessionIsNotFound() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    DOWNSTREAM.resetRequests();

    pay(FakeKeycloak.token("customer-7", "CUSTOMER"), sessionId).expectStatus().isNotFound();

    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
    currentSession().expectStatus().isOk();
  }

  /** Every call to another service, not to Keycloak, in the order they were made. */
  private static List<String> downstreamCalls() {
    return DOWNSTREAM.getAllServeEvents().stream()
        .sorted(Comparator.comparing(e -> e.getRequest().getLoggedDate()))
        .map(ServeEvent::getRequest)
        .filter(r -> !r.getUrl().equals(TOKEN_PATH))
        .map(r -> r.getMethod() + " " + r.getUrl())
        .toList();
  }
}
