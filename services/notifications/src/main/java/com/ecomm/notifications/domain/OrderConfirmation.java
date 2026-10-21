package com.ecomm.notifications.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An {@code ORDER_CONFIRMED} message's content: the paid Order's lines, by Product name, and what
 * it came to. {@code shipping} is {@code null} for an Order placed before Shipping.
 */
public record OrderConfirmation(
    String orderId,
    String customerId,
    Instant placedAt,
    List<Line> lines,
    Money subtotal,
    List<Money> discounts,
    Money shipping,
    Money tax,
    Money total)
    implements MessageContent {

  /** One Order Line: what it is called, how many, and each one's Price. */
  public record Line(String name, int quantity, Money unitPrice) {}

  public OrderConfirmation {
    lines = List.copyOf(lines);
    discounts = List.copyOf(discounts);
  }

  @Override
  public MessageType type() {
    return MessageType.ORDER_CONFIRMED;
  }

  @Override
  public Map<String, Object> model(Locale locale) {
    var order = new LinkedHashMap<String, Object>();
    order.put("reference", Formats.orderReference(orderId));
    order.put("placedOn", Formats.date(placedAt, locale));
    var model = new LinkedHashMap<String, Object>();
    model.put("order", order);
    model.put(
        "lines",
        lines.stream()
            .map(
                line ->
                    Map.of(
                        "name", line.name(),
                        "quantity", line.quantity(),
                        "unitPrice", Formats.money(line.unitPrice(), locale),
                        "lineTotal", Formats.money(lineTotal(line), locale)))
            .toList());
    model.put("subtotal", Formats.money(subtotal, locale));
    model.put("discounts", discounts.stream().map(d -> Formats.money(d, locale)).toList());
    model.put("shipping", shipping == null ? List.of() : List.of(Formats.money(shipping, locale)));
    model.put("tax", Formats.money(tax, locale));
    model.put("total", Formats.money(total, locale));
    return model;
  }

  private static Money lineTotal(Line line) {
    return new Money(line.unitPrice().amountMinor() * line.quantity(), line.unitPrice().currency());
  }
}
