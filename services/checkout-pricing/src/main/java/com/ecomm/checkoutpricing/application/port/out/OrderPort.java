package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.domain.OrderStatus;
import com.ecomm.checkoutpricing.domain.PricedLine;
import java.util.List;

/** Order Management, which owns the Order and its Order Status. */
public interface OrderPort {

  /** Places the Customer's Order in {@code PLACED}; returns its ID. */
  String place(String customerId, List<PricedLine> lines);

  void changeStatus(String customerId, String orderId, OrderStatus status);
}
