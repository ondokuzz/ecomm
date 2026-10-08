package com.ecomm.payment.domain;

import com.ecomm.payment.domain.PaymentTransaction.Kind;
import com.ecomm.payment.domain.PaymentTransaction.Outcome;
import java.util.List;

/**
 * Where a Payment stands with the gateway, worked out from its Payment transactions: {@code
 * PENDING} while its authorization awaits the gateway's answer; {@code AUTHORIZED} once approved
 * and not voided; {@code DECLINED}, final, with the gateway's reason; {@code VOIDED}, an
 * authorization released before capture, final. Capture and refund come later.
 */
public enum PaymentStatus {
  PENDING,
  AUTHORIZED,
  DECLINED,
  VOIDED;

  /**
   * The status {@code transactions}, oldest first, leave a Payment in. The first is its
   * authorization; a pending one may be settled by one more authorization, approved or declined; an
   * authorized or pending Payment may be voided.
   *
   * @throws IllegalStateException for any other sequence, which no Payment can have
   */
  static PaymentStatus of(List<PaymentTransaction> transactions) {
    PaymentStatus status = null;
    for (var transaction : transactions) {
      status = after(status, transaction);
    }
    if (status == null) {
      throw new IllegalStateException("A Payment starts with its authorization");
    }
    return status;
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
    throw new IllegalStateException(
        "A Payment that is "
            + status
            + " can't take a "
            + transaction.outcome()
            + " "
            + transaction.kind());
  }
}
