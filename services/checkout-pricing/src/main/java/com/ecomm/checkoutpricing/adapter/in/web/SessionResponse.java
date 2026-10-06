package com.ecomm.checkoutpricing.adapter.in.web;

import com.ecomm.checkoutpricing.domain.CheckoutSession;
import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.List;

/**
 * A Checkout Session as the Customer sees it; its Reservation stays between Checkout and Inventory.
 * {@code discounts} lists every Discount in the order they apply, empty when there is none.
 */
record SessionResponse(
    String id,
    List<Line> lines,
    Amount subtotal,
    List<Discount> discounts,
    Amount tax,
    Amount total,
    Instant expiresAt) {

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
        Amount.of(session.total()),
        session.expiresAt());
  }
}
