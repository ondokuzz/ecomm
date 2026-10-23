package com.ecomm.notifications.domain;

import java.util.Locale;
import java.util.Map;

/**
 * Where a Customer sends an RMA's units back to a Warehouse, as a parcel is addressed: as
 * Fulfillment & Shipping configures it.
 *
 * @param name who the parcel is addressed to, such as {@code Ecomm Returns Berlin}
 * @param country an ISO 3166-1 alpha-2 code, such as {@code DE}
 */
public record ReturnAddress(
    String name, String street, String postcode, String city, String country) {

  /** Its lines, with the country written out for the locale, such as {@code Germany}. */
  Map<String, Object> model(Locale locale) {
    return Map.of(
        "name", name,
        "street", street,
        "postcode", postcode,
        "city", city,
        "country", new Locale.Builder().setRegion(country).build().getDisplayCountry(locale));
  }
}
