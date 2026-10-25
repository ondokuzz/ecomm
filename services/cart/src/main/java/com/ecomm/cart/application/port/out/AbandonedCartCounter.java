package com.ecomm.cart.application.port.out;

/** Counts Carts as they are marked abandoned. */
public interface AbandonedCartCounter {

  void increment();
}
