package com.ecomm.cart.application.port.in;

import com.ecomm.cart.domain.ChangedCart;
import java.util.Optional;

/** Another service's read of a named Customer's Cart. */
public interface ReadNamedCartUseCase {

  /** The Customer's Cart with its last change; empty if they have none, or it is empty. */
  Optional<ChangedCart> cartOf(String customerId);
}
