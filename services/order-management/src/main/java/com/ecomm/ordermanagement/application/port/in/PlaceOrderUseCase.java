package com.ecomm.ordermanagement.application.port.in;

import com.ecomm.commons.money.Money;
import com.ecomm.ordermanagement.domain.Discount;
import com.ecomm.ordermanagement.domain.Order;
import com.ecomm.ordermanagement.domain.OrderLine;
import java.util.List;
import java.util.Optional;

public interface PlaceOrderUseCase {

  /** Records a new Order for the Customer in {@code PLACED}, with its discount and tax. */
  Order place(String customerId, List<OrderLine> lines, Optional<Discount> discount, Money tax);
}
