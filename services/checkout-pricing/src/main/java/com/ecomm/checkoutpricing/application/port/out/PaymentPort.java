package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.commons.money.Money;

/** Payment, which authorizes an Order's amount for the Customer. */
public interface PaymentPort {

  void authorize(String customerId, String orderId, Money amount);
}
