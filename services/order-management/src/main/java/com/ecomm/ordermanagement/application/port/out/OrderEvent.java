package com.ecomm.ordermanagement.application.port.out;

import com.ecomm.commons.events.IntegrationEvent;
import com.ecomm.commons.money.Money;
import com.ecomm.ordermanagement.domain.Order;
import com.ecomm.ordermanagement.domain.Shipping;
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

  /**
   * The Order's state, its Order Status history oldest first. {@code shippingAddress} and {@code
   * shippingOption} are null, so left out, for an Order placed before Shipping, as are an address's
   * optional fields when it has none.
   */
  public record Snapshot(
      String customerId,
      String status,
      String placedAt,
      List<Line> lines,
      List<Discount> discounts,
      Amount tax,
      Amount subtotal,
      Amount total,
      Address shippingAddress,
      ShippingOption shippingOption,
      List<HistoryEntry> statusHistory) {}

  public record Address(
      String recipientName,
      String line1,
      String line2,
      String postcode,
      String city,
      String region,
      String country,
      String phone) {

    static Address of(Shipping.Address a) {
      return new Address(
          a.recipientName(),
          a.line1(),
          a.line2(),
          a.postcode(),
          a.city(),
          a.region(),
          a.country(),
          a.phone());
    }
  }

  /** The Shipping option chosen, with its Carrier and Shipping cost. */
  public record ShippingOption(String id, String carrier, Amount cost) {

    static ShippingOption of(Shipping.Option o) {
      return new ShippingOption(o.id(), o.carrier(), Amount.of(o.cost()));
    }
  }

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
            order.shipping() == null ? null : Address.of(order.shipping().address()),
            order.shipping() == null ? null : ShippingOption.of(order.shipping().option()),
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
