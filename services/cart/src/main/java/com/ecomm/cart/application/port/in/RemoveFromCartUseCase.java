package com.ecomm.cart.application.port.in;

import com.ecomm.cart.domain.Cart;

public interface RemoveFromCartUseCase {

  /** Takes the Variant out of the Customer's Cart, if it is there. Returns the Cart after. */
  Cart remove(String customerId, String variantId);
}
