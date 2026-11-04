package com.ecomm.supportassistant.application.port.out;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.List;

/** Payment's reads of the Customer's own Payments, made with their own token (ADR 0002). */
public interface PaymentPort {

  /** The Customer's Payments for one of their Orders, newest first; none for another's Order. */
  Lookup<List<PaymentView>> payments(String orderId);

  /**
   * The Customer's Payment transaction history: the Payment transactions on any of their Payments,
   * newest first, at most {@code limit}.
   *
   * @param from inclusive, or null for none
   * @param to exclusive, or null for none
   */
  Lookup<History> transactionHistory(Instant from, Instant to, int limit);

  /**
   * @param declineReason null unless it is {@code DECLINED}
   * @param transactions oldest first
   */
  record PaymentView(
      String id,
      String status,
      Money amount,
      String declineReason,
      List<PaymentTransaction> transactions) {}

  /**
   * One Payment transaction.
   *
   * @param declineReason null unless the gateway declined it
   * @param reference what a Refund is for, such as an RMA reference; null for any other kind
   */
  record PaymentTransaction(
      String kind,
      Money amount,
      String outcome,
      String declineReason,
      Instant at,
      String reference) {}

  /**
   * @param total how many Payment transactions there are in all, beyond {@code lines} too
   */
  record History(List<HistoryLine> lines, long total) {}

  /** A Payment transaction, with its Payment's ID and status now, and its Order. */
  record HistoryLine(
      PaymentTransaction transaction, String paymentId, String paymentStatus, String orderId) {}
}
