package com.ecomm.fulfillmentshipping.domain;

/**
 * A place Orders ship from, defined in configuration. One of them is the default, which ships
 * wherever no Warehouse is in the destination's country.
 *
 * @param id such as {@code WH-BER}
 * @param isDefault whether this is the default Warehouse
 */
public record Warehouse(String id, String name, Address address, boolean isDefault) {

  /**
   * Where the Warehouse is.
   *
   * @param country an ISO 3166-1 alpha-2 code, such as {@code DE}
   */
  public record Address(String line1, String postcode, String city, String country) {}

  public String country() {
    return address.country();
  }
}
