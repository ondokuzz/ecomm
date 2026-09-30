package com.ecomm.checkoutpricing.domain;

import com.ecomm.commons.money.Money;
import java.time.Duration;
import java.time.Instant;

/**
 * A Customer's Cart held for checkout: its lines at the Prices captured when it started, their tax,
 * and the Reservation holding their Stock, until {@code expiresAt}. Paying it honours those Prices
 * whatever Catalog says by then.
 */
public record CheckoutSession(
    String id,
    String customerId,
    PricedCart cart,
    Money tax,
    String reservationId,
    Instant expiresAt) {

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

  /**
   * The subtotal plus the tax, as the Customer is shown it; the Order's own total is what's paid.
   */
  public Money total() {
    var subtotal = cart.subtotal();
    return new Money(Math.addExact(subtotal.amountMinor(), tax.amountMinor()), subtotal.currency());
  }

  public boolean isExpiredAt(Instant now) {
    return !now.isBefore(expiresAt);
  }
}
