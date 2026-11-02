package com.ecomm.payment.domain;

import java.util.Locale;
import java.util.UUID;

/**
 * One line of a Customer's Payment transaction history: a Payment transaction, with the Payment it
 * is on, that Payment's status now, and the Payment's Order.
 */
public record TransactionHistoryLine(
    UUID paymentId, PaymentStatus paymentStatus, String orderId, PaymentTransaction transaction) {

  /** The Order reference: {@code #} and the first eight characters of its ID, upper-cased. */
  public String orderReference() {
    return "#" + orderId.substring(0, Math.min(8, orderId.length())).toUpperCase(Locale.ROOT);
  }
}
