package com.ecomm.payment.domain;

import com.ecomm.commons.money.Money;
import java.util.UUID;

/**
 * A Payment's Refunds come to no more than the amount captured, in its Currency: a Refund of more
 * than is left, or in another Currency, is refused.
 */
public class RefundExceedsCapturedException extends RuntimeException {

  public RefundExceedsCapturedException(UUID id, Money refund, Money left) {
    super(
        "Payment "
            + id
            + " has "
            + left.amountMinor()
            + " "
            + left.currency()
            + " left to refund, not "
            + refund.amountMinor()
            + " "
            + refund.currency());
  }
}
