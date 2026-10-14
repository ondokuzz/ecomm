package com.ecomm.orchestration.application.port.in;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingAddress;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingOption;
import java.time.Duration;
import java.util.List;

/**
 * What the fulfillment Saga is started with: the paid Order, as its {@code PAID} event carried it,
 * with its lines, Shipping address and Shipping option; how long to await delivery once it is
 * shipped; and the Correlation ID of that event, which every step carries.
 */
public record FulfillmentRequest(
    String orderId,
    String customerId,
    List<Line> lines,
    ShippingAddress shippingAddress,
    ShippingOption shippingOption,
    Duration deliveryDeadline,
    String correlationId) {

  /** A Variant and how many of it the Order holds. */
  public record Line(String variantId, int quantity) {}
}
