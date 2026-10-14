package com.ecomm.checkoutpricing.domain;

/** The store doesn't ship to the Shipping address's country, whatever else the address says. */
public class UndeliverableCountryException extends RuntimeException {

  private final String country;

  public UndeliverableCountryException(String country) {
    super("The store doesn't ship to " + country);
    this.country = country;
  }

  public String country() {
    return country;
  }
}
