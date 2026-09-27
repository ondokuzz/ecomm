package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.domain.CartLine;
import com.ecomm.checkoutpricing.domain.Customer;
import java.util.List;

/** The Customer's Cart, reached with the Customer's own token. */
public interface CartPort {

  List<CartLine> lines(Customer customer);

  void clear(Customer customer);
}
