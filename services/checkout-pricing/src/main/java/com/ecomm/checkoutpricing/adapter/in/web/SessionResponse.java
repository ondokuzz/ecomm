package com.ecomm.checkoutpricing.adapter.in.web;

import com.ecomm.checkoutpricing.domain.CheckoutSession;
import com.ecomm.checkoutpricing.domain.ShippingAddress;
import com.ecomm.checkoutpricing.domain.ShippingOption;
import com.ecomm.commons.money.Money;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * A Checkout Session as the Customer sees it; its Reservation stays between Checkout and Inventory.
 * {@code discounts} lists every Discount in the order they apply, empty when there is none. {@code
 * shippingAddress} is null until one is given, and its optional fields are left out when it has
 * none; {@code shippingOptions} are those quoted for it, and {@code shippingOption} the one chosen,
 * null until then, as {@code shippingCost} is zero.
 */
record SessionResponse(
    String id,
    List<Line> lines,
    Amount subtotal,
    List<Discount> discounts,
    Amount tax,
    Address shippingAddress,
    List<Option> shippingOptions,
    Option shippingOption,
    Amount shippingCost,
    Amount total,
    Instant expiresAt) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Address(
      String recipientName,
      String line1,
      String line2,
      String postcode,
      String city,
      String region,
      String country,
      String phone) {

    static Address of(ShippingAddress a) {
      return a == null
          ? null
          : new Address(
              a.recipientName(),
              a.line1(),
              a.line2(),
              a.postcode(),
              a.city(),
              a.region(),
              a.country(),
              a.phone());
    }
  }

  record Option(String id, String carrier, Amount cost, int estimatedDays) {

    static Option of(ShippingOption o) {
      return o == null
          ? null
          : new Option(o.id(), o.carrier(), Amount.of(o.cost()), o.estimatedDays());
    }
  }

  /** A Campaign's names it by ID and name, a Coupon's by its code; the fields it lacks are null. */
  record Discount(
      String source, String couponCode, String campaignId, String campaignName, Amount amount) {}

  record Line(String variantId, int quantity, Amount unitPrice, Amount lineTotal) {}

  record Amount(long amountMinor, String currency) {

    static Amount of(Money money) {
      return new Amount(money.amountMinor(), money.currency().getCurrencyCode());
    }
  }

  static SessionResponse of(CheckoutSession session) {
    return new SessionResponse(
        session.id(),
        session.cart().lines().stream()
            .map(
                l ->
                    new Line(
                        l.variantId(),
                        l.quantity(),
                        Amount.of(l.unitPrice()),
                        Amount.of(l.lineTotal())))
            .toList(),
        Amount.of(session.subtotal()),
        session.discounts().stream()
            .map(
                d ->
                    new Discount(
                        d.source().name(),
                        d.couponCode(),
                        d.campaignId(),
                        d.campaignName(),
                        Amount.of(d.amount())))
            .toList(),
        Amount.of(session.tax()),
        Address.of(session.shippingAddress()),
        session.shippingOptions().stream().map(Option::of).toList(),
        Option.of(session.shippingOption()),
        Amount.of(session.shippingCost()),
        Amount.of(session.total()),
        session.expiresAt());
  }
}
