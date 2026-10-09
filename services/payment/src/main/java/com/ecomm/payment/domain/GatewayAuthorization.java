package com.ecomm.payment.domain;

/**
 * What a payment gateway answered, identified by the gateway's own reference for it: it approved
 * the amount, declined it for a reason, such as {@code insufficient_funds}, or left it pending
 * until the Customer's bank confirms it, when a Gateway webhook settles it as approved or declined.
 */
public sealed interface GatewayAuthorization {

  String reference();

  /** The outcome an authorization transaction records for this answer. */
  PaymentTransaction.Outcome outcome();

  /** The gateway's reason for a decline; null for any other answer. */
  default String declineReason() {
    return null;
  }

  record Approved(String reference) implements GatewayAuthorization {

    @Override
    public PaymentTransaction.Outcome outcome() {
      return PaymentTransaction.Outcome.APPROVED;
    }
  }

  record Declined(String reference, String reason) implements GatewayAuthorization {

    @Override
    public PaymentTransaction.Outcome outcome() {
      return PaymentTransaction.Outcome.DECLINED;
    }

    @Override
    public String declineReason() {
      return reason;
    }
  }

  record Pending(String reference) implements GatewayAuthorization {

    @Override
    public PaymentTransaction.Outcome outcome() {
      return PaymentTransaction.Outcome.PENDING;
    }
  }
}
