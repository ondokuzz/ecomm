package com.ecomm.notifications.application;

import com.ecomm.notifications.application.port.in.ShipmentEventsUseCase;
import com.ecomm.notifications.application.port.out.NotificationPreferencesStore;
import com.ecomm.notifications.application.port.out.NotificationStore;
import com.ecomm.notifications.application.port.out.TimeSource;
import com.ecomm.notifications.application.port.out.Transactions;
import com.ecomm.notifications.domain.MessageContent;
import com.ecomm.notifications.domain.OrderDelivered;
import com.ecomm.notifications.domain.OrderShipped;
import com.ecomm.notifications.domain.ReplacementShipped;
import com.ecomm.notifications.domain.ShippedLine;
import java.util.List;

/**
 * An {@code ORDER} Shipment's change to {@code SHIPPED} records {@code ORDER_SHIPPED}, and to
 * {@code DELIVERED} {@code ORDER_DELIVERED}; a {@code REPLACEMENT}'s change to {@code SHIPPED}
 * records {@code REPLACEMENT_SHIPPED}. Every other change records nothing.
 */
public class ShipmentEventsService implements ShipmentEventsUseCase {

  private final Ledger ledger;
  private final Transactions transactions;

  public ShipmentEventsService(
      NotificationStore notifications,
      NotificationPreferencesStore preferences,
      Transactions transactions,
      TimeSource time) {
    this.ledger = new Ledger(notifications, preferences, time);
    this.transactions = transactions;
  }

  @Override
  public List<String> shipmentChanged(ShipmentChanged event) {
    var content = contentOf(event);
    if (content == null) {
      return List.of();
    }
    return transactions.inTransaction(() -> ledger.record(event.eventId(), content));
  }

  private static MessageContent contentOf(ShipmentChanged e) {
    var lines =
        e.lines().stream().map(l -> new ShippedLine(l.productName(), l.quantity())).toList();
    var replacement = "REPLACEMENT".equals(e.kind());
    return switch (e.change()) {
      case "SHIPPED" ->
          replacement
              ? new ReplacementShipped(
                  e.orderId(), e.rmaId(), e.customerId(), e.carrier(), e.trackingNumber(), lines)
              : new OrderShipped(
                  e.orderId(), e.customerId(), e.carrier(), e.trackingNumber(), lines);
      case "DELIVERED" ->
          replacement
              ? null
              : new OrderDelivered(
                  e.orderId(),
                  e.customerId(),
                  e.deliveredAt() == null ? e.occurredAt() : e.deliveredAt(),
                  lines);
      default -> null;
    };
  }
}
