package com.ecomm.fulfillmentshipping.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.fulfillmentshipping.application.port.in.CreateShipmentUseCase.NewShipment;
import com.ecomm.fulfillmentshipping.domain.Line;
import com.ecomm.fulfillmentshipping.domain.ShippingAddress;
import com.ecomm.fulfillmentshipping.domain.ShippingOption;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * An Order to ship, as the fulfillment Saga sends it from the Order: {@code {"orderId",
 * "customerId", "lines": [{"variantId", "quantity"}], "shippingAddress": {"recipientName", "line1",
 * "line2"?, "postcode", "city", "region"?, "country", "phone"?}, "shippingOption": {"id",
 * "carrier", "cost": {"amountMinor", "currency"}, "estimatedDays"?}}}. Checkout validated the
 * address when the Order was placed, so only what a Shipment can't do without is checked here.
 */
record CreateShipmentRequest(
    String orderId,
    String customerId,
    List<LineRequest> lines,
    AddressRequest shippingAddress,
    OptionRequest shippingOption) {

  private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

  record LineRequest(String variantId, Integer quantity) {}

  record AddressRequest(
      String recipientName,
      String line1,
      String line2,
      String postcode,
      String city,
      String region,
      String country,
      String phone) {}

  record OptionRequest(String id, String carrier, MoneyRequest cost, Integer estimatedDays) {}

  record MoneyRequest(Long amountMinor, String currency) {}

  /**
   * @throws InvalidRequestException naming the first field that's missing or malformed
   */
  NewShipment toNewShipment() {
    required("orderId", orderId);
    required("customerId", customerId);
    return new NewShipment(orderId, customerId, toLines(), toAddress(), toOption());
  }

  private List<Line> toLines() {
    if (lines == null || lines.isEmpty()) {
      throw new InvalidRequestException("lines", "need at least one line");
    }
    var converted = new ArrayList<Line>();
    var variants = new HashSet<String>();
    for (var i = 0; i < lines.size(); i++) {
      var field = "lines[" + i + "]";
      var line = lines.get(i);
      if (line == null) {
        throw new InvalidRequestException(field, "must be an object");
      }
      required(field + ".variantId", line.variantId());
      if (!variants.add(line.variantId())) {
        throw new InvalidRequestException(
            field + ".variantId", "repeats an earlier line's Variant");
      }
      if (line.quantity() == null || line.quantity() < 1) {
        throw new InvalidRequestException(field + ".quantity", "must be a positive integer");
      }
      converted.add(new Line(line.variantId(), line.quantity()));
    }
    return converted;
  }

  private ShippingAddress toAddress() {
    var a = shippingAddress;
    if (a == null) {
      throw new InvalidRequestException("shippingAddress", "is required");
    }
    required("shippingAddress.recipientName", a.recipientName());
    required("shippingAddress.line1", a.line1());
    required("shippingAddress.postcode", a.postcode());
    required("shippingAddress.city", a.city());
    if (a.country() == null || !COUNTRIES.contains(a.country())) {
      throw new InvalidRequestException(
          "shippingAddress.country", "must be an ISO 3166-1 alpha-2 code, such as DE");
    }
    return new ShippingAddress(
        a.recipientName(),
        a.line1(),
        a.line2(),
        a.postcode(),
        a.city(),
        a.region(),
        a.country(),
        a.phone());
  }

  private ShippingOption toOption() {
    var o = shippingOption;
    if (o == null) {
      throw new InvalidRequestException("shippingOption", "is required");
    }
    required("shippingOption.id", o.id());
    required("shippingOption.carrier", o.carrier());
    if (o.cost() == null || o.cost().amountMinor() == null || o.cost().amountMinor() < 0) {
      throw new InvalidRequestException(
          "shippingOption.cost.amountMinor", "must be a non-negative integer");
    }
    Currency currency;
    try {
      currency = Currency.getInstance(o.cost().currency());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidRequestException(
          "shippingOption.cost.currency", "must be an ISO 4217 code, such as EUR");
    }
    if (o.estimatedDays() != null && o.estimatedDays() < 0) {
      throw new InvalidRequestException(
          "shippingOption.estimatedDays", "must be a non-negative integer");
    }
    return new ShippingOption(
        o.id(), o.carrier(), new Money(o.cost().amountMinor(), currency), o.estimatedDays());
  }

  private static void required(String field, String value) {
    if (value == null || value.isBlank()) {
      throw new InvalidRequestException(field, "must be a non-blank string");
    }
  }
}
