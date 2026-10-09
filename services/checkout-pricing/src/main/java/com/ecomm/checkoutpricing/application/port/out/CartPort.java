package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.domain.CartLine;
import java.util.List;

/**
 * The Cart of the Customer checking out. Cart picks the Cart by the Customer's own token, so the
 * adapter speaks for the Customer making the current request.
 */
public interface CartPort {

  List<CartLine> lines();
}
