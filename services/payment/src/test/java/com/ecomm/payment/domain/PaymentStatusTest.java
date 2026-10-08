package com.ecomm.payment.domain;

import static com.ecomm.payment.domain.PaymentStatus.AUTHORIZED;
import static com.ecomm.payment.domain.PaymentStatus.DECLINED;
import static com.ecomm.payment.domain.PaymentStatus.PENDING;
import static com.ecomm.payment.domain.PaymentStatus.VOIDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.commons.money.Money;
import com.ecomm.payment.domain.PaymentTransaction.Kind;
import com.ecomm.payment.domain.PaymentTransaction.Outcome;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A Payment's status is worked out from its Payment transactions, oldest first. Its authorization
 * is approved, declined or pending; a pending one settles once, approved or declined; an approved
 * or pending one can be voided, which is final, as a decline is. Every sequence of up to three
 * transactions is either one of these or impossible, and an impossible one makes no Payment.
 */
class PaymentStatusTest {

  private static final Money AMOUNT = Money.of(79900, "EUR");

  private static final PaymentTransaction APPROVAL = authorization(Outcome.APPROVED, null);
  private static final PaymentTransaction DECLINE = authorization(Outcome.DECLINED, "declined");
  private static final PaymentTransaction PENDING_ANSWER = authorization(Outcome.PENDING, null);
  private static final PaymentTransaction VOID =
      new PaymentTransaction(
          Kind.VOID, AMOUNT, Outcome.APPROVED, "void-ref", null, null, Instant.EPOCH, false);

  static Stream<Arguments> possible() {
    return Stream.of(
        Arguments.of(List.of(APPROVAL), AUTHORIZED),
        Arguments.of(List.of(DECLINE), DECLINED),
        Arguments.of(List.of(PENDING_ANSWER), PENDING),
        Arguments.of(List.of(APPROVAL, VOID), VOIDED),
        Arguments.of(List.of(PENDING_ANSWER, VOID), VOIDED),
        Arguments.of(List.of(PENDING_ANSWER, APPROVAL), AUTHORIZED),
        Arguments.of(List.of(PENDING_ANSWER, DECLINE), DECLINED),
        Arguments.of(List.of(PENDING_ANSWER, APPROVAL, VOID), VOIDED));
  }

  @ParameterizedTest
  @MethodSource("possible")
  void theStatusIsWorkedOutFromTheTransactions(
      List<PaymentTransaction> transactions, PaymentStatus status) {
    assertThat(payment(transactions).status()).isEqualTo(status);
  }

  /** Every sequence of up to three transactions that isn't one of {@link #possible()}. */
  static Stream<List<PaymentTransaction>> impossible() {
    var possible = possible().map(a -> a.get()[0]).toList();
    var all = new ArrayList<List<PaymentTransaction>>();
    List<List<PaymentTransaction>> shorter = List.of(List.of());
    all.addAll(shorter);
    for (var length = 1; length <= 3; length++) {
      var longer = new ArrayList<List<PaymentTransaction>>();
      for (var prefix : shorter) {
        for (var next : List.of(APPROVAL, DECLINE, PENDING_ANSWER, VOID)) {
          var sequence = new ArrayList<>(prefix);
          sequence.add(next);
          longer.add(List.copyOf(sequence));
        }
      }
      all.addAll(longer);
      shorter = longer;
    }
    return all.stream().filter(s -> !possible.contains(s));
  }

  @ParameterizedTest
  @MethodSource("impossible")
  void anImpossibleSequenceMakesNoPayment(List<PaymentTransaction> transactions) {
    assertThatThrownBy(() -> payment(transactions)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aDeclineGivesThePaymentItsReason() {
    assertThat(payment(List.of(PENDING_ANSWER, DECLINE)).declineReason()).isEqualTo("declined");
    assertThat(payment(List.of(APPROVAL)).declineReason()).isNull();
  }

  @Test
  void thePaymentsGatewayReferenceIsItsAuthorizations() {
    assertThat(payment(List.of(APPROVAL, VOID)).gatewayReference()).isEqualTo("auth-ref");
  }

  @Test
  void anAuthorizedPaymentIsVoidedByAVoidTransaction() {
    var voided = payment(List.of(APPROVAL)).voided("void-ref", Instant.EPOCH);

    assertThat(voided.status()).isEqualTo(VOIDED);
    assertThat(voided.transactions()).containsExactly(APPROVAL, VOID);
  }

  @Test
  void aDeclinedPaymentCanNotBeVoided() {
    var declined = payment(List.of(DECLINE));

    assertThat(declined.isVoidable()).isFalse();
    assertThatThrownBy(() -> declined.voided("void-ref", Instant.EPOCH))
        .isInstanceOf(PaymentNotVoidableException.class);
  }

  private static Payment payment(List<PaymentTransaction> transactions) {
    return new Payment(UUID.randomUUID(), "customer-42", "order-1", AMOUNT, transactions);
  }

  private static PaymentTransaction authorization(Outcome outcome, String declineReason) {
    return new PaymentTransaction(
        Kind.AUTHORIZATION, AMOUNT, outcome, "auth-ref", declineReason, null, Instant.EPOCH, false);
  }
}
