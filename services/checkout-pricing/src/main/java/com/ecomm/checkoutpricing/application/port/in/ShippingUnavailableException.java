package com.ecomm.checkoutpricing.application.port.in;

/**
 * Fulfillment & Shipping couldn't quote: it is down, failed, or answered something that couldn't be
 * right. Nothing about the Checkout Session changed.
 */
public class ShippingUnavailableException extends RuntimeException {

  public ShippingUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
