package com.ecomm.payment.domain;

import com.ecomm.commons.money.Money;
import com.ecomm.payment.domain.PaymentTransaction.Kind;
import com.ecomm.payment.domain.PaymentTransaction.Outcome;
import java.util.List;

/**
 * Where a Payment stands with the gateway, worked out from its Payment transactions: {@code
 * PENDING} while its authorization awaits the gateway's answer; {@code AUTHORIZED} once approved
 * and neither voided nor captured; {@code DECLINED}, final, with the gateway's reason; {@code
 * VOIDED}, an authorization released before capture, final; {@code CAPTURED}, the authorized amount
 * charged; {@code PARTIALLY_REFUNDED} once Refunds have given back part of it; {@code REFUNDED},
 * final, once they have given back all of it. A declined capture leaves a Payment {@code
 * AUTHORIZED}, and a declined Refund leaves it as it was.
 */
public enum PaymentStatus {
  PENDING,
  AUTHORIZED,
  DECLINED,
  VOIDED,
  CAPTURED,
  PARTIALLY_REFUNDED,
  REFUNDED;

  /** Whether a Refund can give money back: the Payment is captured, or refunded in part. */
  public boolean isRefundable() {
    return this == CAPTURED || this == PARTIALLY_REFUNDED;
  }

  /** Whether a Capture has charged the Payment: it is captured, or refunded in part or whole. */
  public boolean wasCaptured() {
    return this == CAPTURED || this == PARTIALLY_REFUNDED || this == REFUNDED;
  }

  /**
   * The status {@code transactions}, oldest first, leave a Payment in. The first is its
   * authorization; a pending one may be settled by one more authorization, approved or declined; an
   * authorized or pending Payment may be voided; an authorized one may be captured, approved or
   * declined, and a declined capture leaves it authorized; a captured one may be refunded, approved
   * or declined, in the captured amount's Currency, while the approved Refunds come to no more than
   * the amount captured.
   *
   * @throws IllegalStateException for any other sequence, which no Payment can have
   */
  static PaymentStatus of(List<PaymentTransaction> transactions) {
    return Ledger.of(transactions).status();
  }

  /** What a Payment's transactions come to: its status and what its Refunds have given back. */
  record Ledger(PaymentStatus status, Money captured, long refundedMinor) {

    static Ledger of(List<PaymentTransaction> transactions) {
      PaymentStatus status = null;
      Money captured = null;
      var refundedMinor = 0L;
      for (var transaction : transactions) {
        if (transaction.kind() == Kind.REFUND) {
          refundedMinor += refund(status, captured, refundedMinor, transaction);
          status =
              refundedMinor == 0
                  ? CAPTURED
                  : refundedMinor == captured.amountMinor() ? REFUNDED : PARTIALLY_REFUNDED;
          continue;
        }
        status = after(status, transaction);
        if (status == CAPTURED) {
          captured = transaction.amount();
        }
      }
      if (status == null) {
        throw new IllegalStateException("A Payment starts with its authorization");
      }
      return new Ledger(status, captured, refundedMinor);
    }

    /** What is left to refund; nothing unless the Payment is captured or partly refunded. */
    Money refundable() {
      return status.isRefundable()
          ? new Money(captured.amountMinor() - refundedMinor, captured.currency())
          : null;
    }
  }

  /**
   * What {@code refund} gives back: its amount when approved, nothing when declined.
   *
   * @throws IllegalStateException unless the Payment is captured or partly refunded, and the Refund
   *     is answered, in the captured Currency, and within what is left
   */
  private static long refund(
      PaymentStatus status, Money captured, long refundedMinor, PaymentTransaction refund) {
    if (status == null
        || !status.isRefundable()
        || refund.outcome() == Outcome.PENDING
        || !refund.amount().currency().equals(captured.currency())
        || refund.amount().amountMinor() > captured.amountMinor() - refundedMinor) {
      throw new IllegalStateException(
          "A Payment that is "
              + status
              + " can't take a "
              + refund.outcome()
              + " REFUND of "
              + refund.amount());
    }
    return refund.outcome() == Outcome.APPROVED ? refund.amount().amountMinor() : 0;
  }

  private static PaymentStatus after(PaymentStatus status, PaymentTransaction transaction) {
    if (transaction.kind() == Kind.AUTHORIZATION
        && (status == null || (status == PENDING && transaction.outcome() != Outcome.PENDING))) {
      return switch (transaction.outcome()) {
        case APPROVED -> AUTHORIZED;
        case DECLINED -> DECLINED;
        case PENDING -> PENDING;
      };
    }
    if (transaction.kind() == Kind.VOID
        && transaction.outcome() == Outcome.APPROVED
        && (status == AUTHORIZED || status == PENDING)) {
      return VOIDED;
    }
    if (transaction.kind() == Kind.CAPTURE
        && status == AUTHORIZED
        && transaction.outcome() != Outcome.PENDING) {
      return transaction.outcome() == Outcome.APPROVED ? CAPTURED : AUTHORIZED;
    }
    throw new IllegalStateException(
        "A Payment that is "
            + status
            + " can't take a "
            + transaction.outcome()
            + " "
            + transaction.kind());
  }
}
