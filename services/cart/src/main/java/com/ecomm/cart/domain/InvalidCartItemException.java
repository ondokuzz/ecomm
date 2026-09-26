package com.ecomm.cart.domain;

/** A Cart item was described with a missing Variant or a quantity that isn't positive. */
public class InvalidCartItemException extends RuntimeException {

  public InvalidCartItemException(String message) {
    super(message);
  }
}
