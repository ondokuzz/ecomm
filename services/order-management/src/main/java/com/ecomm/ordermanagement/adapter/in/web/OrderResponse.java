package com.ecomm.ordermanagement.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.ordermanagement.domain.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An Order with the Customer it belongs to, its lines, what it comes to, and its Order Status
 * history, oldest first. {@code discounts} is empty when it has none.
 */
record OrderResponse(
    UUID id,
    String customerId,
    String status,
    List<Line> lines,
    Amount subtotal,
    List<Discount> discounts,
    Amount tax,
    Amount total,
    Instant placedAt,
    List<HistoryEntry> statusHistory) {

  record HistoryEntry(String status, Instant at, String changedBy, boolean backfilled) {}

  record Line(String variantId, int quantity, Amount unitPrice) {}

  /** A Campaign's Discount names it by ID and name, a Coupon's by its code; the others are null. */
  record Discount(
      String source, String couponCode, String campaignId, String campaignName, Amount amount) {}

  record Amount(long amountMinor, String currency) {

    static Amount of(Money money) {
      return new Amount(money.amountMinor(), money.currency().getCurrencyCode());
    }
  }

  static OrderResponse of(Order order) {
    return new OrderResponse(
        order.id(),
        order.customerId(),
        order.status().name(),
        order.lines().stream()
            .map(l -> new Line(l.variantId(), l.quantity(), Amount.of(l.unitPrice())))
            .toList(),
        Amount.of(order.subtotal()),
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
        Amount.of(order.total()),
        order.placedAt(),
        order.statusHistory().stream()
            .map(
                e ->
                    new HistoryEntry(
                        e.status().name(), e.at(), e.changedBy().name(), e.backfilled()))
            .toList());
  }
}
