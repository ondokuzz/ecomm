package com.ecomm.notifications.adapter.in.kafka;

import com.ecomm.commons.money.Money;
import com.ecomm.notifications.application.port.in.OrderEventsUseCase;
import com.ecomm.notifications.application.port.in.OrderEventsUseCase.OrderChanged;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Order Management's Order events, which trigger the Order's Notifications. */
@Component
class OrderListener {

  private static final Logger log = LoggerFactory.getLogger(OrderListener.class);

  /**
   * The fields of an {@code order-management.order} event Notifications needs. The change and
   * Status are read as text, since Order Management may add one; an event missing a field
   * Notifications can't do without can't be read, and is dead-lettered at once.
   */
  record OrderEvent(
      @JsonProperty(required = true) String eventId,
      @JsonProperty(required = true) String orderId,
      @JsonProperty(required = true) String change,
      @JsonProperty(required = true) Order order) {
    record Order(
        @JsonProperty(required = true) String customerId,
        @JsonProperty(required = true) String status,
        @JsonProperty(required = true) Instant placedAt,
        @JsonProperty(required = true) List<Line> lines,
        @JsonProperty(required = true) List<Discount> discounts,
        @JsonProperty(required = true) MoneyJson subtotal,
        @JsonProperty(required = true) MoneyJson tax,
        @JsonProperty(required = true) MoneyJson total,
        Option shippingOption) {}

    record Line(
        @JsonProperty(required = true) String variantId,
        @JsonProperty(required = true) int quantity,
        @JsonProperty(required = true) MoneyJson unitPrice) {}

    record Discount(@JsonProperty(required = true) MoneyJson amount) {}

    record Option(@JsonProperty(required = true) MoneyJson cost) {}

    record MoneyJson(
        @JsonProperty(required = true) long amountMinor,
        @JsonProperty(required = true) String currency) {
      Money toMoney() {
        return Money.of(amountMinor, currency);
      }
    }
  }

  private final OrderEventsUseCase orders;

  OrderListener(OrderEventsUseCase orders) {
    this.orders = orders;
  }

  /**
   * On its first start the group reads from the latest offset, so nothing that happened before
   * Notifications existed is announced; from then on it resumes where it left off, so an Order paid
   * while Notifications was down is still confirmed.
   */
  @KafkaListener(
      topics = "order-management.order",
      groupId = "notifications.orders",
      properties = "auto.offset.reset=latest")
  void on(OrderEvent event) {
    var order = event.order();
    var recorded =
        orders.orderChanged(
            new OrderChanged(
                event.eventId(),
                event.orderId(),
                event.change(),
                order.customerId(),
                order.status(),
                order.placedAt(),
                order.lines().stream()
                    .map(
                        l ->
                            new OrderChanged.Line(
                                l.variantId(), l.quantity(), l.unitPrice().toMoney()))
                    .toList(),
                order.subtotal().toMoney(),
                order.discounts().stream().map(d -> d.amount().toMoney()).toList(),
                order.shippingOption() == null ? null : order.shippingOption().cost().toMoney(),
                order.tax().toMoney(),
                order.total().toMoney()));
    if (!recorded.isEmpty()) {
      log.info(
          "Recorded {} for Order {} from event {}", recorded, event.orderId(), event.eventId());
    }
  }
}
