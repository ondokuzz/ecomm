package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.checkoutpricing.domain.PaymentAttempt.Status;
import com.ecomm.checkoutpricing.domain.PricedLine;
import com.ecomm.commons.money.Money;
import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Paying a Checkout Session starts the checkout Saga with the session as it stands, and answers
 * with how it ended, or that it is still going. Checkout calls no other service to pay. An expired,
 * unknown or invalid payment starts nothing.
 */
class PaySessionApiTest extends CheckoutApiTest {

  @Test
  void payingStartsTheSagaWithTheSessionAsItStands() {
    stubSuccessfulCheckout();
    stubDiscount("WELCOME10", 15980);
    var sessionId = startedSessionId();
    applyCoupon(sessionId, "welcome10").expectStatus().isOk();
    stubVariant("PHN-PIXEL-9", 99900);
    DOWNSTREAM.resetRequests();

    pay(sessionId).expectStatus().isOk();

    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
    assertThat(saga.started()).hasSize(1);
    var started = saga.started().getFirst();
    assertThat(started.paymentMethod()).isEqualTo(APPROVE);
    var session = started.session();
    assertThat(session.id()).isEqualTo(sessionId);
    assertThat(session.customerId()).isEqualTo(CUSTOMER_ID);
    assertThat(session.reservationId()).isEqualTo(RESERVATION_ID);
    assertThat(session.cart().lines())
        .containsExactly(
            new PricedLine("PHN-PIXEL-9", "PHN-PIXEL-9", "phones", 2, Money.of(79900, "EUR")));
    assertThat(session.discounts())
        .containsExactly(Discount.coupon("WELCOME10", Money.of(15980, "EUR")));
    assertThat(session.tax()).isEqualTo(Money.of(0, "EUR"));
  }

  @Test
  void aPaidOrderIsOk() {
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
  void aDeclineIsPaymentRequiredWithItsReason() {
    stubSuccessfulCheckout();
    saga.answer(Status.DECLINED, "insufficient_funds");

    checkout()
        .expectStatus()
        .isEqualTo(402)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.declineReason")
        .isEqualTo("insufficient_funds");
  }

  @Test
  void aHoldThatRanOutIsGoneSayingSo() {
    stubSuccessfulCheckout();
    saga.answer(Status.HOLD_EXPIRED);

    checkout()
        .expectStatus()
        .isEqualTo(410)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("holdExpired");
  }

  @Test
  void aFailedSagaIsABadGatewayWithTheCorrelationId() {
    stubSuccessfulCheckout();
    saga.answer(Status.FAILED);

    checkout()
        .expectStatus()
        .isEqualTo(502)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.correlationId")
        .isNotEmpty();
  }

  @Test
  void aSagaStillRunningIsAcceptedAndThePaymentEndpointThenReportsHowItEnded() {
    stubSuccessfulCheckout();
    saga.answer(Status.PROCESSING);
    var sessionId = startedSessionId();

    pay(sessionId)
        .expectStatus()
        .isAccepted()
        .expectHeader()
        .location("/checkout/sessions/" + sessionId + "/payment")
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("PROCESSING");
    payment(sessionId)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("PROCESSING");

    saga.finish(sessionId, Status.PAID);

    payment(sessionId)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("PAID")
        .jsonPath("$.orderId")
        .isEqualTo(ORDER_ID);
  }

  @Test
  void thePaymentEndpointGivesADeclinesReason() {
    stubSuccessfulCheckout();
    saga.answer(Status.DECLINED, "card_declined");
    var sessionId = startedSessionId();
    pay(sessionId).expectStatus().isEqualTo(402);

    payment(sessionId)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("DECLINED")
        .jsonPath("$.declineReason")
        .isEqualTo("card_declined");
  }

  @Test
  void aSecondPayWhileTheFirstRunsJoinsIt() {
    stubSuccessfulCheckout();
    saga.answer(Status.PROCESSING);
    var sessionId = startedSessionId();
    pay(sessionId).expectStatus().isAccepted();

    pay(sessionId).expectStatus().isAccepted();

    assertThat(saga.started()).hasSize(1);
  }

  @Test
  void theSessionCanBePaidAgainAfterADecline() {
    stubSuccessfulCheckout();
    saga.answer(Status.DECLINED, "card_declined");
    var sessionId = startedSessionId();
    payWithMethod(sessionId, "tok_decline").expectStatus().isEqualTo(402);

    saga.answer(Status.PAID);
    payWithMethod(sessionId, APPROVE).expectStatus().isOk();

    assertThat(saga.started())
        .extracting(FakeCheckoutSaga.Started::paymentMethod)
        .containsExactly("tok_decline", APPROVE);
  }

  @Test
  void aPaidSessionIsNeverPaidTwice() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    pay(sessionId).expectStatus().isOk();

    pay(sessionId).expectStatus().isOk().expectBody().jsonPath("$.orderId").isEqualTo(ORDER_ID);

    assertThat(saga.started()).hasSize(1);
  }

  @Test
  void anUnreachableSagaIsServiceUnavailableAndStartsNothing() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    saga.unreachable();

    pay(sessionId)
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("checkoutUnavailable");
    assertThat(saga.started()).isEmpty();
  }

  @Test
  void thePaymentEndpointStillAnswersOnceTheSessionHasEnded() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    pay(sessionId).expectStatus().isOk();

    clock.advance(Duration.ofHours(1));

    payment(sessionId).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("PAID");
  }

  @Test
  void aSessionNeverPaidHasNoPayment() {
    stubSuccessfulCheckout();

    payment(startedSessionId())
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void anotherCustomersPaymentIsNotFound() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    pay(sessionId).expectStatus().isOk();

    payment(FakeKeycloak.token("customer-7", "CUSTOMER"), sessionId).expectStatus().isNotFound();
  }

  @Test
  void payingAnExpiredSessionIsGoneAndStartsNothing() {
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
    assertThat(saga.started()).isEmpty();
  }

  @Test
  void aSessionJustShortOfItsExpiryCanStillBePaid() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    clock.advance(Duration.ofMinutes(15).minusSeconds(5));

    pay(sessionId).expectStatus().isOk();
  }

  @Test
  void payingAnUnknownSessionIsNotFoundAndStartsNothing() {
    stubSuccessfulCheckout();

    pay("0b7e6a52-0000-4000-8000-000000000009")
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(saga.started()).isEmpty();
  }

  @Test
  void anotherCustomersSessionIsNotFoundAndStartsNothing() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    pay(FakeKeycloak.token("customer-7", "CUSTOMER"), sessionId).expectStatus().isNotFound();

    assertThat(saga.started()).isEmpty();
    currentSession().expectStatus().isOk();
  }
}
