package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import com.ecomm.payment.application.port.in.PublishBackfillUseCase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Payments recorded before the ledger (a Sprint 3 stack's, from {@code db/testdata}) become
 * Payments with one backfilled authorization, and are published once with change {@code BACKFILLED}
 * when the service starts; starting again publishes nothing more.
 */
class BackfillApiTest extends PaymentApiTest {

  private static final String CUSTOMER = "customer-sprint3";
  private static final String AUTHORIZED = "5e000000-0000-4000-8000-0000000000a1";
  private static final String DECLINED = "5e000000-0000-4000-8000-0000000000a2";
  private static final String VOIDABLE = "5e000000-0000-4000-8000-0000000000a3";

  @Autowired PublishBackfillUseCase backfill;

  @Test
  void anAuthorizedPaymentGetsAnApprovedBackfilledAuthorization() {
    var payment = read(AUTHORIZED);

    assertThat(payment.status()).isEqualTo("AUTHORIZED");
    assertThat(payment.gatewayReference()).isEqualTo("mock-sprint3-approved");
    assertThat(payment.transactions()).hasSize(1);
    var authorization = payment.transactions().getFirst();
    assertThat(authorization.kind()).isEqualTo("AUTHORIZATION");
    assertThat(authorization.outcome()).isEqualTo("APPROVED");
    assertThat(authorization.amount()).isEqualTo(new AmountView(79900, "EUR"));
    assertThat(authorization.gatewayReference()).isEqualTo("mock-sprint3-approved");
    assertThat(authorization.backfilled()).isTrue();
  }

  @Test
  void aDeclinedPaymentGetsADeclinedBackfilledAuthorizationWithItsReason() {
    var payment = read(DECLINED);

    assertThat(payment.status()).isEqualTo("DECLINED");
    assertThat(payment.declineReason()).isEqualTo("card_declined");
    assertThat(payment.transactions())
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.outcome()).isEqualTo("DECLINED");
              assertThat(t.declineReason()).isEqualTo("card_declined");
              assertThat(t.backfilled()).isTrue();
            });
  }

  @Test
  void staffSeeEveryBackfilledPaymentForAnOrder() {
    assertThat(staffPayments("order-sprint3-cancelled"))
        .extracting(PaymentView::id)
        .containsExactlyInAnyOrder(DECLINED, VOIDABLE);
  }

  @Test
  void everyExistingPaymentIsPublishedOnceAsBackfilled() {
    var authorized = PaymentEvents.keyed(AUTHORIZED, 1);
    var declined = PaymentEvents.keyed(DECLINED, 1);

    assertThat(authorized).hasSize(1);
    assertThat(declined).hasSize(1);
    assertThat(PaymentEvents.schemaViolationsOf(authorized.getFirst())).isEmpty();
    assertThat(PaymentEvents.schemaViolationsOf(declined.getFirst())).isEmpty();
    var authorizedEvent = PaymentEvents.valueOf(authorized.getFirst());
    assertThat(authorizedEvent.get("change").asText()).isEqualTo("BACKFILLED");
    assertThat(authorizedEvent.get("version").asLong()).isEqualTo(1);
    assertThat(authorizedEvent.at("/payment/status").asText()).isEqualTo("AUTHORIZED");
    assertThat(authorizedEvent.at("/payment/transactions/0/backfilled").asBoolean()).isTrue();
    var declinedEvent = PaymentEvents.valueOf(declined.getFirst());
    assertThat(declinedEvent.at("/payment/status").asText()).isEqualTo("DECLINED");
    assertThat(declinedEvent.at("/payment/declineReason").asText()).isEqualTo("card_declined");
  }

  @Test
  void runningTheBackfillAgainPublishesNothingMore() {
    PaymentEvents.keyed(AUTHORIZED, 1);

    assertThat(backfill.publishBackfill()).isZero();

    assertThat(PaymentEvents.keyed(AUTHORIZED, 2, Duration.ofSeconds(3))).hasSize(1);
    assertThat(PaymentEvents.keyed(DECLINED, 2, Duration.ofSeconds(3))).hasSize(1);
  }

  @Test
  void aBackfilledPaymentCanBeVoided() {
    PaymentEvents.keyed(VOIDABLE, 1);

    voidPayment(VOIDABLE, CUSTOMER).expectStatus().isOk();

    var events = PaymentEvents.keyed(VOIDABLE, 2).stream().map(PaymentEvents::valueOf).toList();
    assertThat(events)
        .extracting(e -> e.get("change").asText())
        .containsExactly("BACKFILLED", "VOIDED");
    assertThat(events).extracting(e -> e.get("version").asLong()).containsExactly(1L, 2L);
    assertThat(read(VOIDABLE).transactions())
        .extracting(TransactionView::backfilled)
        .containsExactly(true, false);
  }

  private PaymentView read(String id) {
    return http.get()
        .uri("/payments/{id}", id)
        .headers(h -> h.setBearerAuth(FakeKeycloak.token(CUSTOMER, "CUSTOMER")))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(PaymentView.class)
        .returnResult()
        .getResponseBody();
  }
}
