package com.ecomm.cart.application.port.out;

import com.ecomm.cart.domain.AbandonedCart;
import com.ecomm.cart.domain.Cart;
import com.ecomm.cart.domain.CartItem;
import java.time.Instant;
import java.util.List;

/**
 * Every change to a Cart keeps it for another {@code Cart.LIFETIME}, records when it was made, and
 * makes the Cart a candidate for abandonment again.
 */
public interface CartRepository {

  /** The Customer's Cart; empty if they have none or it has expired. */
  Cart find(String customerId);

  /**
   * Sets the item's Variant to its quantity in the Customer's Cart, creating the Cart if need be.
   */
  void put(String customerId, CartItem item, Instant changedAt);

  /**
   * Takes the Variant out of the Customer's Cart; does nothing if it isn't there. A Cart left empty
   * is gone, and never abandoned.
   */
  void remove(String customerId, String variantId, Instant changedAt);

  /** Deletes the Customer's Cart, if they have one; it is never abandoned. */
  void delete(String customerId);

  /**
   * Up to {@code limit} Carts that hold Variants, last changed at or before {@code cutoff}, and not
   * yet marked abandoned since; oldest first.
   */
  List<AbandonedCart> idleSince(Instant cutoff, int limit);

  /**
   * Marks the Cart abandoned, unless it has changed since {@code abandoned.lastChangedAt()}.
   *
   * @return whether it marked it
   */
  boolean markAbandoned(AbandonedCart abandoned);
}
