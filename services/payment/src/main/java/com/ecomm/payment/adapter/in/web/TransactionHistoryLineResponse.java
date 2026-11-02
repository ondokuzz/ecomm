package com.ecomm.payment.adapter.in.web;

import com.ecomm.payment.adapter.in.web.PaymentResponse.Amount;
import com.ecomm.payment.domain.TransactionHistoryLine;
import java.util.UUID;

/**
 * One line of a Customer's Payment transaction history: the Payment transaction as the Order page
 * shows it, {@code declineReason} null unless the gateway declined it and {@code reference} null
 * unless it is a Refund, with its Payment's ID and status now, its Order's ID and its Order
 * reference.
 */
record TransactionHistoryLineResponse(
    String kind,
    Amount amount,
    String outcome,
    String declineReason,
    String at,
    String reference,
    UUID paymentId,
    String paymentStatus,
    String orderId,
    String orderReference) {

  static TransactionHistoryLineResponse of(TransactionHistoryLine line) {
    var transaction = line.transaction();
    return new TransactionHistoryLineResponse(
        transaction.kind().name(),
        Amount.of(transaction.amount()),
        transaction.outcome().name(),
        transaction.declineReason(),
        transaction.at().toString(),
        transaction.refundFor(),
        line.paymentId(),
        line.paymentStatus().name(),
        line.orderId(),
        line.orderReference());
  }
}
