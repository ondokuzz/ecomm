package com.ecomm.checkoutpricing.application.port.in;

import com.ecomm.checkoutpricing.domain.CheckoutResult;
import com.ecomm.checkoutpricing.domain.Customer;

public interface CheckoutUseCase {

  /**
   * Turns the Customer's Cart into a paid Order and clears the Cart. If a step fails once the Order
   * exists, the Order is cancelled and the Cart is left as it was.
   */
  CheckoutResult checkout(Customer customer);
}
