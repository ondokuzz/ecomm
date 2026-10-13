package com.ecomm.fulfillmentshipping.domain;

/** No Carrier offers any Shipping option to the destination in the Currency. */
public class ShippingUnavailableException extends RuntimeException {

  public ShippingUnavailableException(String country, String currency) {
    super("No Shipping option ships to " + country + " in " + currency);
  }
}
