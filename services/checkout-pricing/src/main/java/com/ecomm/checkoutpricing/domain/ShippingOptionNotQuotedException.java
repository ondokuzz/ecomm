package com.ecomm.checkoutpricing.domain;

/** A Shipping option was chosen that wasn't quoted for the Checkout Session's Shipping address. */
public class ShippingOptionNotQuotedException extends RuntimeException {

  public ShippingOptionNotQuotedException(String optionId) {
    super("The Shipping option " + optionId + " wasn't quoted for this Shipping address");
  }
}
