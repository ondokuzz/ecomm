package com.ecomm.cart.application.port.in;

import com.ecomm.cart.domain.Cart;

public interface ViewCartUseCase {

  /** The Customer's Cart; empty if they have none. */
  Cart cart(String customerId);
}
