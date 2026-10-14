package com.ecomm.payment.domain;

import java.util.UUID;

/**
 * Only an authorized Payment can be captured: a pending one awaits its answer, and a declined or
 * voided one has nothing to charge.
 */
public class PaymentNotCapturableException extends RuntimeException {

  public PaymentNotCapturableException(UUID id, PaymentStatus status) {
    super("Payment " + id + " is " + status + " and can't be captured");
  }
}
