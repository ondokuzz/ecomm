package com.ecomm.checkoutpricing.adapter.in.web;

import com.ecomm.checkoutpricing.domain.CheckoutSession;
import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.List;

/**
 * A Checkout Session as the Customer sees it; its Reservation stays between Checkout and Inventory.
 * {@code discount} is null when no Coupon is applied.
 */
record SessionResponse(
    String id,
    List<Line> lines,
    Amount subtotal,
    Discount discount,
    Amount tax,
    Amount total,
    Instant expiresAt) {

  record Discount(String couponCode, Amount amount) {}

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
        session.discount() == null
            ? null
            : new Discount(session.discount().couponCode(), Amount.of(session.discount().amount())),
        Amount.of(session.tax()),
        Amount.of(session.total()),
        session.expiresAt());
  }
}
