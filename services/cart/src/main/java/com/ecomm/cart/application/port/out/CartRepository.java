package com.ecomm.cart.application.port.out;

import com.ecomm.cart.domain.Cart;
import com.ecomm.cart.domain.CartItem;

/** Every change to a Cart keeps it for another {@code Cart.LIFETIME}. */
public interface CartRepository {

  /** The Customer's Cart; empty if they have none or it has expired. */
  Cart find(String customerId);

  /**
   * Sets the item's Variant to its quantity in the Customer's Cart, creating the Cart if need be.
   */
  void put(String customerId, CartItem item);

  /** Takes the Variant out of the Customer's Cart; does nothing if it isn't there. */
  void remove(String customerId, String variantId);

  /** Deletes the Customer's Cart, if they have one. */
  void delete(String customerId);
}
