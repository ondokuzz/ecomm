package com.ecomm.cart.application.port.in;

import com.ecomm.cart.domain.Cart;
import com.ecomm.cart.domain.CartItem;

public interface SetQuantityUseCase {

  /** Puts the item's Variant in the Customer's Cart at its quantity. Returns the Cart after. */
  Cart setQuantity(String customerId, CartItem item);
}
