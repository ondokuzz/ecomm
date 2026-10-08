package com.ecomm.payment.domain;

import java.util.UUID;

/** A Payment that isn't authorized or pending, such as a declined one, has nothing to release. */
public class PaymentNotVoidableException extends RuntimeException {

  public PaymentNotVoidableException(UUID id, PaymentStatus status) {
    super("Payment " + id + " is " + status + " and can't be voided");
  }
}
