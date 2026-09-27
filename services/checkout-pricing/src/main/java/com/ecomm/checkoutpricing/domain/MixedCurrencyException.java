package com.ecomm.checkoutpricing.domain;

/** The Cart's Variants are priced in more than one currency, which one Order can't hold. */
public class MixedCurrencyException extends RuntimeException {

  public MixedCurrencyException() {
    super("The Cart's Variants are priced in more than one currency.");
  }
}
