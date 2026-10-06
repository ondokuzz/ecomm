package com.ecomm.ordermanagement.application.port.out;

import com.ecomm.commons.events.IntegrationEvent;
import com.ecomm.commons.money.Money;
import com.ecomm.ordermanagement.domain.Order;
import java.util.List;

/**
 * The {@code order-management.order} event: a snapshot of an Order, published when it is placed,
 * whenever its Status changes, and once for every Order that existed before Order events. Its shape
 * is defined by {@code platform/event-schemas/schemas/order-management.order.json}.
 */
public record OrderEvent(String orderId, long version, Change change, Snapshot order)
    implements IntegrationEvent {

  public static final String TOPIC = "order-management.order";

  /** Why the event was published. */
  public enum Change {
    PLACED,
    STATUS_CHANGED,
    BACKFILLED
  }

  /** The Order's state, its Order Status history oldest first. */
  public record Snapshot(
      String customerId,
      String status,
      String placedAt,
      List<Line> lines,
      List<Discount> discounts,
      Amount tax,
      Amount subtotal,
      Amount total,
      List<HistoryEntry> statusHistory) {}

  public record Line(String variantId, int quantity, Amount unitPrice) {}

  /** One Discount: a Campaign's names it by ID and name, a Coupon's by its code. */
  public record Discount(
      String source, String couponCode, String campaignId, String campaignName, Amount amount) {}

  /** {@code backfilled} is null, so left out, unless the entry was reconstructed. */
  public record HistoryEntry(String status, String at, String changedBy, Boolean backfilled) {}

  public record Amount(long amountMinor, String currency) {

    static Amount of(Money money) {
      return new Amount(money.amountMinor(), money.currency().getCurrencyCode());
    }
  }

  public static OrderEvent of(Order order, Change change) {
    return new OrderEvent(
        order.id().toString(),
        order.version(),
        change,
        new Snapshot(
            order.customerId(),
            order.status().name(),
            order.placedAt().toString(),
            order.lines().stream()
                .map(l -> new Line(l.variantId(), l.quantity(), Amount.of(l.unitPrice())))
                .toList(),
            order.discounts().stream()
                .map(
                    d ->
                        new Discount(
                            d.source().name(),
                            d.couponCode(),
                            d.campaignId(),
                            d.campaignName(),
                            Amount.of(d.amount())))
                .toList(),
            Amount.of(order.tax()),
            Amount.of(order.subtotal()),
            Amount.of(order.total()),
            order.statusHistory().stream()
                .map(
                    e ->
                        new HistoryEntry(
                            e.status().name(),
                            e.at().toString(),
                            e.changedBy().name(),
                            e.backfilled() ? true : null))
                .toList()));
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public String aggregateId() {
    return orderId;
  }
}
