package com.ecomm.ordermanagement.application.port.in;

import com.ecomm.ordermanagement.domain.Order;
import com.ecomm.ordermanagement.domain.OrderLine;
import java.util.List;

public interface PlaceOrderUseCase {

  /** Records a new Order for the Customer in {@code PLACED}. */
  Order place(String customerId, List<OrderLine> lines);
}
