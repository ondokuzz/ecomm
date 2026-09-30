package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.domain.PaymentDeclinedException;
import com.ecomm.commons.money.Money;

/** Payment, which authorizes an Order's amount for the Customer. */
public interface PaymentPort {

  /**
   * Authorizes {@code amount} for the Order, paid with {@code paymentMethod}, the gateway's token
   * for the Customer's card.
   *
   * @throws PaymentDeclinedException when the gateway declines it
   */
  void authorize(String customerId, String orderId, Money amount, String paymentMethod);
}
