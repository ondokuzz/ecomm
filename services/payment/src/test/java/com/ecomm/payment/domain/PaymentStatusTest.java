package com.ecomm.payment.domain;

import static com.ecomm.payment.domain.PaymentStatus.AUTHORIZED;
import static com.ecomm.payment.domain.PaymentStatus.CAPTURED;
import static com.ecomm.payment.domain.PaymentStatus.DECLINED;
import static com.ecomm.payment.domain.PaymentStatus.PARTIALLY_REFUNDED;
import static com.ecomm.payment.domain.PaymentStatus.PENDING;
import static com.ecomm.payment.domain.PaymentStatus.REFUNDED;
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
 * or pending one can be voided, which is final, as a decline is. An approved one can be captured,
 * which is final until it is refunded; a declined capture leaves it authorized, to be captured
 * again or voided. A captured one can be refunded, in parts, up to the amount captured: it is
 * partially refunded until the Refunds reach that amount, then refunded, which is final; a declined
 * Refund changes nothing. Every sequence of up to three transactions is either one of these or
 * impossible, and an impossible one makes no Payment.
 */
class PaymentStatusTest {

  private static final Money AMOUNT = Money.of(79900, "EUR");

  private static final PaymentTransaction APPROVAL = authorization(Outcome.APPROVED, null);
  private static final PaymentTransaction DECLINE = authorization(Outcome.DECLINED, "declined");
  private static final PaymentTransaction PENDING_ANSWER = authorization(Outcome.PENDING, null);
  private static final PaymentTransaction VOID =
      new PaymentTransaction(
          Kind.VOID, AMOUNT, Outcome.APPROVED, "void-ref", null, null, Instant.EPOCH, false, null);
  private static final PaymentTransaction CAPTURE = capture(Outcome.APPROVED, null);
  private static final PaymentTransaction CAPTURE_DECLINE =
      capture(Outcome.DECLINED, "authorization_expired");
  private static final PaymentTransaction PART_REFUND =
      refund(Money.of(30000, "EUR"), Outcome.APPROVED, null);
  private static final PaymentTransaction FULL_REFUND = refund(AMOUNT, Outcome.APPROVED, null);
  private static final PaymentTransaction REFUND_DECLINE =
      refund(AMOUNT, Outcome.DECLINED, "refund_window_closed");

  static Stream<Arguments> possible() {
    return Stream.of(
        Arguments.of(List.of(APPROVAL), AUTHORIZED),
        Arguments.of(List.of(DECLINE), DECLINED),
        Arguments.of(List.of(PENDING_ANSWER), PENDING),
        Arguments.of(List.of(APPROVAL, VOID), VOIDED),
        Arguments.of(List.of(PENDING_ANSWER, VOID), VOIDED),
        Arguments.of(List.of(PENDING_ANSWER, APPROVAL), AUTHORIZED),
        Arguments.of(List.of(PENDING_ANSWER, DECLINE), DECLINED),
        Arguments.of(List.of(PENDING_ANSWER, APPROVAL, VOID), VOIDED),
        Arguments.of(List.of(APPROVAL, CAPTURE), CAPTURED),
        Arguments.of(List.of(PENDING_ANSWER, APPROVAL, CAPTURE), CAPTURED),
        Arguments.of(List.of(APPROVAL, CAPTURE_DECLINE), AUTHORIZED),
        Arguments.of(List.of(PENDING_ANSWER, APPROVAL, CAPTURE_DECLINE), AUTHORIZED),
        Arguments.of(List.of(APPROVAL, CAPTURE_DECLINE, CAPTURE), CAPTURED),
        Arguments.of(List.of(APPROVAL, CAPTURE_DECLINE, CAPTURE_DECLINE), AUTHORIZED),
        Arguments.of(List.of(APPROVAL, CAPTURE_DECLINE, VOID), VOIDED),
        Arguments.of(List.of(APPROVAL, CAPTURE, PART_REFUND), PARTIALLY_REFUNDED),
        Arguments.of(List.of(APPROVAL, CAPTURE, FULL_REFUND), REFUNDED),
        Arguments.of(List.of(APPROVAL, CAPTURE, REFUND_DECLINE), CAPTURED));
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
        for (var next :
            List.of(
                APPROVAL,
                DECLINE,
                PENDING_ANSWER,
                VOID,
                CAPTURE,
                CAPTURE_DECLINE,
                PART_REFUND,
                FULL_REFUND,
                REFUND_DECLINE)) {
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

  @Test
  void aPendingCaptureIsImpossible() {
    assertThatThrownBy(() -> payment(List.of(APPROVAL, capture(Outcome.PENDING, null))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aDeclinedCaptureLeavesThePaymentWithoutADeclineReason() {
    assertThat(payment(List.of(APPROVAL, CAPTURE_DECLINE)).declineReason()).isNull();
  }

  @Test
  void anAuthorizedPaymentIsCapturedByACaptureTransaction() {
    var captured = payment(List.of(APPROVAL)).captured("capture-ref", Instant.EPOCH);

    assertThat(captured.status()).isEqualTo(CAPTURED);
    assertThat(captured.transactions()).containsExactly(APPROVAL, CAPTURE);
  }

  @Test
  void anAuthorizedPaymentRecordsADeclinedCapture() {
    var refused =
        payment(List.of(APPROVAL))
            .captureDeclined("capture-ref", "authorization_expired", Instant.EPOCH);

    assertThat(refused.status()).isEqualTo(AUTHORIZED);
    assertThat(refused.transactions()).containsExactly(APPROVAL, CAPTURE_DECLINE);
  }

  @Test
  void aCapturedPaymentCanNotBeVoided() {
    var captured = payment(List.of(APPROVAL, CAPTURE));

    assertThat(captured.isVoidable()).isFalse();
    assertThatThrownBy(() -> captured.voided("void-ref", Instant.EPOCH))
        .isInstanceOf(PaymentNotVoidableException.class);
  }

  @ParameterizedTest
  @MethodSource("notCapturable")
  void onlyAnAuthorizedPaymentCanBeCaptured(List<PaymentTransaction> transactions) {
    var payment = payment(transactions);

    assertThatThrownBy(() -> payment.captured("capture-ref", Instant.EPOCH))
        .isInstanceOf(PaymentNotCapturableException.class);
    assertThatThrownBy(() -> payment.captureDeclined("capture-ref", "expired", Instant.EPOCH))
        .isInstanceOf(PaymentNotCapturableException.class);
  }

  static Stream<List<PaymentTransaction>> notCapturable() {
    return Stream.of(
        List.of(PENDING_ANSWER),
        List.of(DECLINE),
        List.of(APPROVAL, VOID),
        List.of(APPROVAL, CAPTURE));
  }

  @Test
  void refundsAddUpToTheAmountCaptured() {
    var rest = refund(Money.of(49900, "EUR"), Outcome.APPROVED, null);

    assertThat(payment(List.of(APPROVAL, CAPTURE, PART_REFUND, PART_REFUND)).status())
        .isEqualTo(PARTIALLY_REFUNDED);
    assertThat(payment(List.of(APPROVAL, CAPTURE, PART_REFUND, rest)).status()).isEqualTo(REFUNDED);
    var partDeclined = refund(Money.of(30000, "EUR"), Outcome.DECLINED, "refund_window_closed");
    assertThat(payment(List.of(APPROVAL, CAPTURE, PART_REFUND, partDeclined)).status())
        .isEqualTo(PARTIALLY_REFUNDED);
  }

  @Test
  void refundsBeyondTheAmountCapturedAreImpossible() {
    var tooMuch = refund(Money.of(49901, "EUR"), Outcome.APPROVED, null);

    assertThatThrownBy(() -> payment(List.of(APPROVAL, CAPTURE, PART_REFUND, tooMuch)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> payment(List.of(APPROVAL, CAPTURE, FULL_REFUND, REFUND_DECLINE)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aRefundInAnotherCurrencyIsImpossible() {
    var dollars = refund(Money.of(100, "USD"), Outcome.APPROVED, null);

    assertThatThrownBy(() -> payment(List.of(APPROVAL, CAPTURE, dollars)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aPendingRefundIsImpossible() {
    assertThatThrownBy(
            () -> payment(List.of(APPROVAL, CAPTURE, refund(AMOUNT, Outcome.PENDING, null))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aCapturedPaymentIsRefundedUpToWhatIsLeft() {
    var captured = payment(List.of(APPROVAL, CAPTURE));
    assertThat(captured.refundable()).isEqualTo(AMOUNT);

    var partly = captured.refunded(Money.of(30000, "EUR"), "refund-ref", "RMA-1", Instant.EPOCH);

    assertThat(partly.status()).isEqualTo(PARTIALLY_REFUNDED);
    assertThat(partly.refundable()).isEqualTo(Money.of(49900, "EUR"));
    assertThat(partly.transactions().getLast())
        .isEqualTo(
            new PaymentTransaction(
                Kind.REFUND,
                Money.of(30000, "EUR"),
                Outcome.APPROVED,
                "refund-ref",
                null,
                null,
                Instant.EPOCH,
                false,
                "RMA-1"));
    assertThat(
            partly
                .refunded(Money.of(49900, "EUR"), "refund-ref-2", "RMA-2", Instant.EPOCH)
                .status())
        .isEqualTo(REFUNDED);
  }

  @Test
  void aDeclinedRefundIsRecordedAndChangesNothing() {
    var refused =
        payment(List.of(APPROVAL, CAPTURE))
            .refundDeclined(AMOUNT, "refund-ref", "refund_window_closed", "RMA-1", Instant.EPOCH);

    assertThat(refused.status()).isEqualTo(CAPTURED);
    assertThat(refused.declineReason()).isNull();
    assertThat(refused.transactions().getLast().declineReason()).isEqualTo("refund_window_closed");
  }

  @Test
  void aRefundBeyondWhatIsLeftIsRefused() {
    var partly = payment(List.of(APPROVAL, CAPTURE, PART_REFUND));

    assertThatThrownBy(
            () -> partly.refunded(Money.of(49901, "EUR"), "refund-ref", "RMA-1", Instant.EPOCH))
        .isInstanceOf(RefundExceedsCapturedException.class);
    assertThatThrownBy(
            () -> partly.refunded(Money.of(100, "USD"), "refund-ref", "RMA-1", Instant.EPOCH))
        .isInstanceOf(RefundExceedsCapturedException.class);
  }

  @ParameterizedTest
  @MethodSource("notRefundable")
  void onlyACapturedOrPartlyRefundedPaymentCanBeRefunded(List<PaymentTransaction> transactions) {
    var payment = payment(transactions);

    assertThatThrownBy(
            () -> payment.refunded(Money.of(100, "EUR"), "refund-ref", "RMA-1", Instant.EPOCH))
        .isInstanceOf(PaymentNotRefundableException.class);
    assertThatThrownBy(
            () ->
                payment.refundDeclined(
                    Money.of(100, "EUR"), "refund-ref", "expired", "RMA-1", Instant.EPOCH))
        .isInstanceOf(PaymentNotRefundableException.class);
  }

  static Stream<List<PaymentTransaction>> notRefundable() {
    return Stream.of(
        List.of(PENDING_ANSWER),
        List.of(APPROVAL),
        List.of(DECLINE),
        List.of(APPROVAL, VOID),
        List.of(APPROVAL, CAPTURE, FULL_REFUND));
  }

  @Test
  void aRefundedPaymentCanNotBeVoided() {
    assertThat(payment(List.of(APPROVAL, CAPTURE, PART_REFUND)).isVoidable()).isFalse();
  }

  private static Payment payment(List<PaymentTransaction> transactions) {
    return new Payment(UUID.randomUUID(), "customer-42", "order-1", AMOUNT, transactions);
  }

  private static PaymentTransaction authorization(Outcome outcome, String declineReason) {
    return new PaymentTransaction(
        Kind.AUTHORIZATION,
        AMOUNT,
        outcome,
        "auth-ref",
        declineReason,
        null,
        Instant.EPOCH,
        false,
        null);
  }

  private static PaymentTransaction capture(Outcome outcome, String declineReason) {
    return new PaymentTransaction(
        Kind.CAPTURE,
        AMOUNT,
        outcome,
        "capture-ref",
        declineReason,
        null,
        Instant.EPOCH,
        false,
        null);
  }

  private static PaymentTransaction refund(Money amount, Outcome outcome, String declineReason) {
    return new PaymentTransaction(
        Kind.REFUND,
        amount,
        outcome,
        "refund-ref",
        declineReason,
        null,
        Instant.EPOCH,
        false,
        "RMA-1");
  }
}
