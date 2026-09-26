package com.ecomm.cart.application.port.in;

public interface ClearCartUseCase {

  /** Empties the Customer's Cart. */
  void clear(String customerId);
}
