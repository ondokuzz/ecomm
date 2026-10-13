package com.ecomm.ordermanagement.domain;

import com.ecomm.commons.money.Money;
import java.util.Locale;
import java.util.Set;

/**
 * Where an Order goes and how: the Shipping address Checkout validated, and the Shipping option the
 * Customer chose, with its Carrier and the Shipping cost they were quoted. Throws {@link
 * InvalidOrderException} unless each part is there and well formed.
 */
public record Shipping(Address address, Option option) {

  public Shipping {
    if (address == null) {
      throw new InvalidOrderException("an order needs a shippingAddress");
    }
    if (option == null) {
      throw new InvalidOrderException("an order needs a shippingOption");
    }
  }

  /**
   * A Shipping address: its recipient, one or two lines, its postcode, city and optional region,
   * its ISO 3166-1 alpha-2 country and an optional phone. The optional fields are null when absent.
   */
  public record Address(
      String recipientName,
      String line1,
      String line2,
      String postcode,
      String city,
      String region,
      String country,
      String phone) {

    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

    public Address {
      required(recipientName, "recipientName", 100);
      required(line1, "line1", 100);
      optional(line2, "line2", 100);
      required(postcode, "postcode", 16);
      required(city, "city", 100);
      optional(region, "region", 100);
      if (country == null || !COUNTRIES.contains(country)) {
        throw new InvalidOrderException(
            "shippingAddress.country must be an ISO 3166-1 alpha-2 code, such as DE");
      }
      optional(phone, "phone", 32);
    }

    private static void required(String value, String field, int maxLength) {
      if (value == null || value.isBlank()) {
        throw new InvalidOrderException("shippingAddress." + field + " is required");
      }
      optional(value, field, maxLength);
    }

    private static void optional(String value, String field, int maxLength) {
      if (value != null && value.length() > maxLength) {
        throw new InvalidOrderException(
            "shippingAddress." + field + " is at most " + maxLength + " characters");
      }
    }
  }

  /** The Shipping option chosen: its ID, such as {@code EXPRESS}, its Carrier and its cost. */
  public record Option(String id, String carrier, Money cost) {

    public Option {
      if (id == null || id.isBlank() || id.length() > 32) {
        throw new InvalidOrderException("shippingOption.id is required, at most 32 characters");
      }
      if (carrier == null || carrier.isBlank() || carrier.length() > 64) {
        throw new InvalidOrderException(
            "shippingOption.carrier is required, at most 64 characters");
      }
      if (cost == null) {
        throw new InvalidOrderException("shippingOption.cost is required");
      }
      if (cost.amountMinor() < 0) {
        throw new InvalidOrderException("shippingOption.cost can't be negative");
      }
    }
  }
}
