package com.ecomm.ordermanagement.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.ordermanagement.domain.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An Order with its lines, and what it comes to: {@code discount} is null when it has none. */
record OrderResponse(
    UUID id,
    String status,
    List<Line> lines,
    Amount subtotal,
    Discount discount,
    Amount tax,
    Amount total,
    Instant placedAt) {

  record Line(String variantId, int quantity, Amount unitPrice) {}

  record Discount(String couponCode, Amount amount) {}

  record Amount(long amountMinor, String currency) {

    static Amount of(Money money) {
      return new Amount(money.amountMinor(), money.currency().getCurrencyCode());
    }
  }

  static OrderResponse of(Order order) {
    return new OrderResponse(
        order.id(),
        order.status().name(),
        order.lines().stream()
            .map(l -> new Line(l.variantId(), l.quantity(), Amount.of(l.unitPrice())))
            .toList(),
        Amount.of(order.subtotal()),
        order.discount().map(d -> new Discount(d.couponCode(), Amount.of(d.amount()))).orElse(null),
        Amount.of(order.tax()),
        Amount.of(order.total()),
        order.placedAt());
  }
}
