package com.ecomm.ordermanagement.application;

import com.ecomm.ordermanagement.application.port.in.ChangeOrderStatusUseCase;
import com.ecomm.ordermanagement.application.port.in.ConcurrentStatusChangeException;
import com.ecomm.ordermanagement.application.port.in.FindOrdersUseCase;
import com.ecomm.ordermanagement.application.port.in.PlaceOrderUseCase;
import com.ecomm.ordermanagement.application.port.out.OrderRepository;
import com.ecomm.ordermanagement.application.port.out.TimeSource;
import com.ecomm.ordermanagement.domain.Order;
import com.ecomm.ordermanagement.domain.OrderLine;
import com.ecomm.ordermanagement.domain.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OrderService
    implements PlaceOrderUseCase, FindOrdersUseCase, ChangeOrderStatusUseCase {

  private final OrderRepository orders;
  private final TimeSource time;

  public OrderService(OrderRepository orders, TimeSource time) {
    this.orders = orders;
    this.time = time;
  }

  @Override
  public Order place(String customerId, List<OrderLine> lines) {
    var order = Order.place(UUID.randomUUID(), customerId, lines, time.now());
    orders.add(order);
    return order;
  }

  @Override
  public Optional<Order> order(String customerId, UUID id) {
    return orders.find(id).filter(order -> order.belongsTo(customerId));
  }

  @Override
  public List<Order> orders(String customerId) {
    return orders.findByCustomer(customerId);
  }

  @Override
  public Optional<Order> changeStatus(String customerId, UUID id, OrderStatus next) {
    return order(customerId, id)
        .map(
            order -> {
              var changed = order.changeStatusTo(next);
              if (!orders.replaceStatus(id, order.status(), next)) {
                throw new ConcurrentStatusChangeException(id);
              }
              return changed;
            });
  }
}
