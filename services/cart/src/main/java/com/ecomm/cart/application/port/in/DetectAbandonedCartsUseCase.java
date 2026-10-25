package com.ecomm.cart.application.port.in;

public interface DetectAbandonedCartsUseCase {

  /**
   * Publishes each Cart idle past the threshold and not yet abandoned, then marks it abandoned.
   * Does nothing while another sweep, in this instance or another, holds the lock.
   *
   * @return how many Carts it marked abandoned
   */
  int sweep();
}
