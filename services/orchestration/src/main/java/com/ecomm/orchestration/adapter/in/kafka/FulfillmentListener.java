package com.ecomm.orchestration.adapter.in.kafka;

import com.ecomm.commons.web.CorrelationId;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingAddress;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingOption;
import com.ecomm.orchestration.application.port.in.FulfillmentRequest.Line;
import com.ecomm.orchestration.application.port.in.FulfillmentUseCase;
import com.ecomm.orchestration.application.port.in.FulfillmentUseCase.OrderChanged;
import com.ecomm.orchestration.application.port.in.ShipmentChange;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Starts and wakes the fulfillment Saga from Order Management's Order events and Fulfillment &
 * Shipping's Shipment events, one consumer group per topic (ADR 0009).
 */
@Component
class FulfillmentListener {

  /**
   * The fields of an {@code order-management.order} event the Saga needs. Its Status is read as
   * text, since Order Management may add one; its Shipping is absent on an Order placed before
   * Sprint 5. An event missing a field the Saga can't do without is dead-lettered at once.
   */
  record OrderEvent(
      @JsonProperty(required = true) String orderId,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) Order order) {

    record Order(
        @JsonProperty(required = true) String customerId,
        @JsonProperty(required = true) String status,
        @JsonProperty(required = true) List<OrderLine> lines,
        Address shippingAddress,
        Option shippingOption) {}

    record OrderLine(
        @JsonProperty(required = true) String variantId,
        @JsonProperty(required = true) int quantity) {}

    record Address(
        String recipientName,
        String line1,
        String line2,
        String postcode,
        String city,
        String region,
        String country,
        String phone) {}

    record Option(String id, String carrier, Money cost) {}

    record Money(long amountMinor, String currency) {}
  }

  /** The fields of a {@code fulfillment-shipping.shipment} event the Saga is woken with. */
  record ShipmentEvent(
      @JsonProperty(required = true) String shipmentId,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) Shipment shipment) {

    record Shipment(
        @JsonProperty(required = true) String orderId,
        @JsonProperty(required = true) String status) {}
  }

  private final FulfillmentUseCase fulfillment;

  FulfillmentListener(FulfillmentUseCase fulfillment) {
    this.fulfillment = fulfillment;
  }

  /**
   * On its first start the group reads from the latest offset, so Orders paid before the Saga
   * existed aren't fulfilled; from then on it resumes where it left off, so an Order paid while
   * Orchestration was down is fulfilled once it is back.
   */
  @KafkaListener(
      topics = "order-management.order",
      groupId = "orchestration.orders",
      properties = "auto.offset.reset=latest")
  void on(OrderEvent event) {
    var order = event.order();
    fulfillment.orderChanged(
        new OrderChanged(
            event.orderId(),
            order.customerId(),
            order.status(),
            order.lines().stream().map(l -> new Line(l.variantId(), l.quantity())).toList(),
            addressOf(order.shippingAddress()),
            optionOf(order.shippingOption()),
            CorrelationId.current().orElse(null)));
  }

  @KafkaListener(topics = "fulfillment-shipping.shipment", groupId = "orchestration.shipments")
  void on(ShipmentEvent event) {
    var shipment = event.shipment();
    fulfillment.shipmentChanged(
        shipment.orderId(),
        new ShipmentChange(event.shipmentId(), event.version(), shipment.status()));
  }

  private static ShippingAddress addressOf(OrderEvent.Address a) {
    return a == null
        ? null
        : new ShippingAddress(
            a.recipientName(),
            a.line1(),
            a.line2(),
            a.postcode(),
            a.city(),
            a.region(),
            a.country(),
            a.phone());
  }

  private static ShippingOption optionOf(OrderEvent.Option o) {
    return o == null
        ? null
        : new ShippingOption(
            o.id(), o.carrier(), new Amount(o.cost().amountMinor(), o.cost().currency()));
  }
}
