package com.ecomm.ordermanagement.application;

import com.ecomm.commons.events.IntegrationEventPublisher;
import com.ecomm.commons.money.Money;
import com.ecomm.ordermanagement.application.port.in.BrowseOrdersUseCase;
import com.ecomm.ordermanagement.application.port.in.ChangeOrderStatusUseCase;
import com.ecomm.ordermanagement.application.port.in.ConcurrentStatusChangeException;
import com.ecomm.ordermanagement.application.port.in.FindOrdersUseCase;
import com.ecomm.ordermanagement.application.port.in.OrderFilter;
import com.ecomm.ordermanagement.application.port.in.Page;
import com.ecomm.ordermanagement.application.port.in.PlaceOrderUseCase;
import com.ecomm.ordermanagement.application.port.in.PublishBackfillUseCase;
import com.ecomm.ordermanagement.application.port.out.OrderEvent;
import com.ecomm.ordermanagement.application.port.out.OrderEvent.Change;
import com.ecomm.ordermanagement.application.port.out.OrderRepository;
import com.ecomm.ordermanagement.application.port.out.TimeSource;
import com.ecomm.ordermanagement.application.port.out.Transactions;
import com.ecomm.ordermanagement.domain.Caller;
import com.ecomm.ordermanagement.domain.Discount;
import com.ecomm.ordermanagement.domain.Order;
import com.ecomm.ordermanagement.domain.OrderLine;
import com.ecomm.ordermanagement.domain.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OrderService
    implements PlaceOrderUseCase,
        FindOrdersUseCase,
        BrowseOrdersUseCase,
        ChangeOrderStatusUseCase,
        PublishBackfillUseCase {

  /** How many Orders the backfill publishes per transaction. */
  private static final int BACKFILL_BATCH = 100;

  private final OrderRepository orders;
  private final Transactions transactions;
  private final IntegrationEventPublisher events;
  private final TimeSource time;

  public OrderService(
      OrderRepository orders,
      Transactions transactions,
      IntegrationEventPublisher events,
      TimeSource time) {
    this.orders = orders;
    this.transactions = transactions;
    this.events = events;
    this.time = time;
  }

  @Override
  public Order place(
      Caller caller,
      String customerId,
      List<OrderLine> lines,
      Optional<Discount> discount,
      Money tax) {
    var order =
        Order.place(UUID.randomUUID(), customerId, lines, discount, tax, caller, time.now());
    return transactions.inTransaction(
        () -> {
          orders.add(order);
          events.publish(OrderEvent.of(order, Change.PLACED));
          return order;
        });
  }

  @Override
  public Optional<Order> order(String customerId, UUID id) {
    return orders.find(id).filter(order -> order.belongsTo(customerId));
  }

  @Override
  public Page<Order> orders(String customerId, int page, int size) {
    return new Page<>(
        orders.findByCustomer(customerId, (long) page * size, size),
        page,
        size,
        orders.countByCustomer(customerId));
  }

  @Override
  public Optional<Order> anyOrder(UUID id) {
    return orders.find(id);
  }

  @Override
  public Page<Order> orders(OrderFilter filter, int page, int size) {
    return new Page<>(
        orders.findMatching(filter, (long) page * size, size),
        page,
        size,
        orders.countMatching(filter));
  }

  @Override
  public Optional<Order> changeStatus(Caller caller, String customerId, UUID id, OrderStatus next) {
    return transactions.inTransaction(
        () ->
            order(customerId, id)
                .map(
                    order -> {
                      var changed = order.changeStatusTo(next, caller, time.now());
                      if (!orders.recordStatusChange(changed)) {
                        throw new ConcurrentStatusChangeException(id);
                      }
                      events.publish(OrderEvent.of(changed, Change.STATUS_CHANGED));
                      return changed;
                    }));
  }

  @Override
  public int publishBackfill() {
    var published = 0;
    while (true) {
      var batch =
          transactions.inTransaction(
              () -> {
                var awaiting = orders.lockAwaitingBackfillEvent(BACKFILL_BATCH);
                awaiting.forEach(order -> events.publish(OrderEvent.of(order, Change.BACKFILLED)));
                orders.markBackfillPublished(awaiting.stream().map(Order::id).toList());
                return awaiting.size();
              });
      if (batch == 0) {
        return published;
      }
      published += batch;
    }
  }
}
