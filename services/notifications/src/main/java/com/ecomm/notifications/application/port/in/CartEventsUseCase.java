package com.ecomm.notifications.application.port.in;

import java.time.Instant;
import java.util.List;

/** Cart's events: an Abandoned Cart may get a Cart reminder. */
public interface CartEventsUseCase {

  /** The fields of a {@code cart.cart} {@code ABANDONED} event a Cart reminder needs. */
  record CartAbandonedEvent(String eventId, String customerId, Instant lastChangedAt) {}

  /**
   * What was recorded, such as {@code CART_ABANDONED EMAIL}; empty when nothing was.
   *
   * @throws com.ecomm.notifications.application.port.out.Carts.CartsUnavailableException when Cart
   *     can't answer now, so the event is retried
   * @throws
   *     com.ecomm.notifications.application.port.out.ListedProducts.ListingsUnavailableException
   *     when Search & Discovery can't answer now, so the event is retried
   */
  List<String> cartAbandoned(CartAbandonedEvent event);
}
