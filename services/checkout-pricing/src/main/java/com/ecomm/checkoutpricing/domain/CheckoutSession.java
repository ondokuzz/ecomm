package com.ecomm.checkoutpricing.domain;

import com.ecomm.commons.money.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A Customer's Cart held for checkout: its lines at the Prices captured when it started, every
 * Discount it is due (the running Campaigns', then the applied Coupon's, if any), their tax, and
 * the Reservation holding their Stock, until {@code expiresAt}. Paying it honours those Prices
 * whatever Catalog says by then.
 */
public record CheckoutSession(
    String id,
    String customerId,
    PricedCart cart,
    List<Discount> discounts,
    Money tax,
    String reservationId,
    Instant expiresAt) {

  public CheckoutSession {
    discounts = List.copyOf(discounts);
  }

  /** How long a Checkout Session holds the Cart. */
  public static final Duration LIFETIME = Duration.ofMinutes(15);

  /**
   * How much longer the Reservation holds the Stock than the session, so a payment that starts just
   * before the session expires can still commit it.
   */
  public static final Duration RESERVATION_GRACE = Duration.ofMinutes(2);

  /** When a Reservation for a session that expires at {@code expiresAt} should expire. */
  public static Instant reservationExpiresAt(Instant expiresAt) {
    return expiresAt.plus(RESERVATION_GRACE);
  }

  public Money subtotal() {
    return cart.subtotal();
  }

  /** What every Discount together takes off the subtotal: nothing without one. */
  public Money discountAmount() {
    return Discount.total(discounts, subtotal().currency());
  }

  /** The code of the Coupon applied, as Promotions upper-cased it; empty without one. */
  public Optional<String> couponCode() {
    return discounts.stream()
        .filter(d -> d.source() == Discount.Source.COUPON)
        .map(Discount::couponCode)
        .findFirst();
  }

  /**
   * The subtotal less every Discount, plus the tax, as the Customer is shown it; the Order's own
   * total is what's paid.
   */
  public Money total() {
    var subtotal = cart.subtotal();
    return new Money(
        Math.addExact(
            Math.subtractExact(subtotal.amountMinor(), discountAmount().amountMinor()),
            tax.amountMinor()),
        subtotal.currency());
  }

  /** This session with {@code discounts} in place of its own, and the tax that comes with them. */
  public CheckoutSession withDiscounts(List<Discount> discounts, Money tax) {
    return new CheckoutSession(id, customerId, cart, discounts, tax, reservationId, expiresAt);
  }

  public boolean isExpiredAt(Instant now) {
    return !now.isBefore(expiresAt);
  }
}
