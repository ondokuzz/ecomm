package com.ecomm.cart.application.port.out;

import com.ecomm.cart.domain.AbandonedCart;

/**
 * Publishes {@code cart.cart} {@code ABANDONED} events. Cart has no transaction to share, so there
 * is no outbox: the event is derived from the Cart's state, with an ID derived from the Customer
 * and {@code lastChangedAt}, and publishing the same abandonment again publishes the same event.
 */
public interface AbandonedCartPublisher {

  /**
   * Returns once the event is durably published.
   *
   * @throws IllegalStateException if it couldn't be published
   */
  void publish(AbandonedCart abandoned);
}
