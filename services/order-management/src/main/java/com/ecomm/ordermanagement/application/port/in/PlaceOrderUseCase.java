package com.ecomm.ordermanagement.application.port.in;

import com.ecomm.commons.money.Money;
import com.ecomm.ordermanagement.domain.Caller;
import com.ecomm.ordermanagement.domain.Discount;
import com.ecomm.ordermanagement.domain.Order;
import com.ecomm.ordermanagement.domain.OrderLine;
import com.ecomm.ordermanagement.domain.Shipping;
import java.util.List;

public interface PlaceOrderUseCase {

  /**
   * Records a new Order for the Customer in {@code PLACED}, with its Discounts, tax and Shipping,
   * and its placement by {@code caller} as the first entry in its Order Status history.
   */
  Order place(
      Caller caller,
      String customerId,
      List<OrderLine> lines,
      List<Discount> discounts,
      Money tax,
      Shipping shipping);
}
