package com.ecomm.checkoutpricing.domain;

/** A Checkout Session was paid before it had a Shipping address and a chosen Shipping option. */
public class ShippingIncompleteException extends RuntimeException {

  public ShippingIncompleteException(String sessionId) {
    super(
        "Checkout Session "
            + sessionId
            + " needs a Shipping address and a Shipping option before it can be paid");
  }
}
