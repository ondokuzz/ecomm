package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.checkoutpricing.domain.OrderStatus;
import com.ecomm.checkoutpricing.domain.PlacedOrder;
import com.ecomm.checkoutpricing.domain.PricedLine;
import com.ecomm.commons.money.Money;
import java.util.List;

/** Order Management, which owns the Order and its Order Status. */
public interface OrderPort {

  /**
   * Places the Customer's Order in {@code PLACED}, with every Discount, in the order they applied,
   * and its tax; returns its ID and the total Order Management gives it.
   */
  PlacedOrder place(String customerId, List<PricedLine> lines, List<Discount> discounts, Money tax);

  void changeStatus(String customerId, String orderId, OrderStatus status);
}
