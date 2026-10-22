package com.ecomm.notifications.application;

import com.ecomm.notifications.application.port.in.OrderEventsUseCase;
import com.ecomm.notifications.application.port.out.NotificationPreferencesStore;
import com.ecomm.notifications.application.port.out.NotificationStore;
import com.ecomm.notifications.application.port.out.ProductNames;
import com.ecomm.notifications.application.port.out.TimeSource;
import com.ecomm.notifications.application.port.out.Transactions;
import com.ecomm.notifications.domain.OrderConfirmation;
import java.util.List;

/**
 * An Order's change to {@code PAID} records an {@code ORDER_CONFIRMED} Notification by email, its
 * lines named from the Product projection, or by Variant ID while it hasn't caught up. Recording
 * claims the event, Message type and channel, so a redelivered event records nothing more.
 */
public class OrderEventsService implements OrderEventsUseCase {

  private final Ledger ledger;
  private final ProductNames products;
  private final Transactions transactions;

  public OrderEventsService(
      NotificationStore notifications,
      NotificationPreferencesStore preferences,
      ProductNames products,
      Transactions transactions,
      TimeSource time) {
    this.ledger = new Ledger(notifications, preferences, time);
    this.products = products;
    this.transactions = transactions;
  }

  @Override
  public List<String> orderChanged(OrderChanged event) {
    if (!"STATUS_CHANGED".equals(event.change()) || !"PAID".equals(event.status())) {
      return List.of();
    }
    return transactions.inTransaction(
        () -> {
          var names =
              products.namesOf(event.lines().stream().map(OrderChanged.Line::variantId).toList());
          var content =
              new OrderConfirmation(
                  event.orderId(),
                  event.customerId(),
                  event.placedAt(),
                  event.lines().stream()
                      .map(
                          l ->
                              new OrderConfirmation.Line(
                                  names.getOrDefault(l.variantId(), l.variantId()),
                                  l.quantity(),
                                  l.unitPrice()))
                      .toList(),
                  event.subtotal(),
                  event.discounts(),
                  event.shipping(),
                  event.tax(),
                  event.total());
          return ledger.record(event.eventId(), content);
        });
  }
}
