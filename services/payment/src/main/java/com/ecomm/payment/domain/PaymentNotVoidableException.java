package com.ecomm.payment.domain;

import java.util.UUID;

/**
 * A Payment that isn't authorized or pending has nothing to release: a declined one was never
 * authorized, and a captured one has been charged, so only a refund returns its money.
 */
public class PaymentNotVoidableException extends RuntimeException {

  private final PaymentStatus status;

  public PaymentNotVoidableException(UUID id, PaymentStatus status) {
    super("Payment " + id + " is " + status + " and can't be voided");
    this.status = status;
  }

  public PaymentStatus status() {
    return status;
  }
}
