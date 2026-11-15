package com.ecomm.cart.domain;

import java.time.Instant;

/**
 * A Customer's Cart as it stands, with when it last changed: what a service checks an abandonment
 * against, since a Cart changed after {@code lastChangedAt} is no longer the one abandoned.
 */
public record ChangedCart(String customerId, Cart cart, Instant lastChangedAt) {

  /** {@code lastChangedAt} in epoch milliseconds, as {@code cart.cart} events carry it. */
  public long version() {
    return lastChangedAt.toEpochMilli();
  }
}
