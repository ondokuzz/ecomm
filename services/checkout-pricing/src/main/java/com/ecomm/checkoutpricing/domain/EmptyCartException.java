package com.ecomm.checkoutpricing.domain;

/** The Customer's Cart holds nothing to check out. */
public class EmptyCartException extends RuntimeException {

  public EmptyCartException() {
    super("The Cart is empty; there is nothing to check out.");
  }
}
