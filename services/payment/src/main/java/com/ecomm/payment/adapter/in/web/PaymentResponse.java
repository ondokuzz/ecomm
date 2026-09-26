package com.ecomm.payment.adapter.in.web;

import com.ecomm.payment.domain.Payment;
import java.util.UUID;

record PaymentResponse(
    UUID id, String orderId, Amount amount, String status, String gatewayReference) {

  record Amount(long amountMinor, String currency) {}

  static PaymentResponse of(Payment payment) {
    return new PaymentResponse(
        payment.id(),
        payment.orderId(),
        new Amount(payment.amount().amountMinor(), payment.amount().currency().getCurrencyCode()),
        payment.status().name(),
        payment.gatewayReference());
  }
}
