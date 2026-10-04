package com.ecomm.ordermanagement.application.port.in;

import com.ecomm.commons.money.Money;
import com.ecomm.ordermanagement.domain.Caller;
import com.ecomm.ordermanagement.domain.Discount;
import com.ecomm.ordermanagement.domain.Order;
import com.ecomm.ordermanagement.domain.OrderLine;
import java.util.List;
import java.util.Optional;

public interface PlaceOrderUseCase {

  /**
   * Records a new Order for the Customer in {@code PLACED}, with its discount and tax, and its
   * placement by {@code caller} as the first entry in its Order Status history.
   */
  Order place(
      Caller caller,
      String customerId,
      List<OrderLine> lines,
      Optional<Discount> discount,
      Money tax);
}
