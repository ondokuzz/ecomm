package com.ecomm.payment.domain;

import java.util.UUID;

/**
 * Only money that was charged can be given back: a Payment must be captured, or partly refunded, to
 * be refunded.
 */
public class PaymentNotRefundableException extends RuntimeException {

  public PaymentNotRefundableException(UUID id, PaymentStatus status) {
    super("Payment " + id + " is " + status + " and can't be refunded");
  }
}
