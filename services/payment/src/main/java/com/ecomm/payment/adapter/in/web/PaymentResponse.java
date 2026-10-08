package com.ecomm.payment.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.payment.domain.Payment;
import com.ecomm.payment.domain.PaymentTransaction;
import java.util.List;
import java.util.UUID;

/**
 * A Payment with its Payment transactions, oldest first: {@code declineReason} is null unless its
 * {@code status} is {@code DECLINED}, and its {@code gatewayReference} is its authorization's.
 */
record PaymentResponse(
    UUID id,
    String orderId,
    Amount amount,
    String status,
    String declineReason,
    String gatewayReference,
    List<PaymentTransactionResponse> transactions) {

  record Amount(long amountMinor, String currency) {

    static Amount of(Money money) {
      return new Amount(money.amountMinor(), money.currency().getCurrencyCode());
    }
  }

  /**
   * One Payment transaction: {@code declineReason} and {@code gatewayEventId} are null unless the
   * gateway declined it or a webhook recorded it; {@code backfilled} marks one reconstructed from
   * before the ledger, whose {@code at} is approximate.
   */
  record PaymentTransactionResponse(
      String kind,
      Amount amount,
      String outcome,
      String gatewayReference,
      String declineReason,
      String gatewayEventId,
      String at,
      boolean backfilled) {

    static PaymentTransactionResponse of(PaymentTransaction transaction) {
      return new PaymentTransactionResponse(
          transaction.kind().name(),
          Amount.of(transaction.amount()),
          transaction.outcome().name(),
          transaction.gatewayReference(),
          transaction.declineReason(),
          transaction.gatewayEventId(),
          transaction.at().toString(),
          transaction.backfilled());
    }
  }

  static PaymentResponse of(Payment payment) {
    return new PaymentResponse(
        payment.id(),
        payment.orderId(),
        Amount.of(payment.amount()),
        payment.status().name(),
        payment.declineReason(),
        payment.gatewayReference(),
        payment.transactions().stream().map(PaymentTransactionResponse::of).toList());
  }
}
