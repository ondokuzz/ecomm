package com.ecomm.orchestration.application;

import com.ecomm.orchestration.application.port.in.FulfillmentRequest;
import com.ecomm.orchestration.application.port.in.FulfillmentUseCase;
import com.ecomm.orchestration.application.port.in.ShipmentChange;
import com.ecomm.orchestration.application.port.out.FulfillmentWorkflows;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts an Order's fulfillment when its {@code PAID} event comes with a Shipping address, and
 * passes its Shipment's events on to it. A start refused because the Order had one already, or a
 * signal with no fulfillment to wake, is expected: events are delivered at least once, and
 * replayed.
 */
public class FulfillmentService implements FulfillmentUseCase {

  private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);

  private final FulfillmentWorkflows workflows;
  private final Duration deliveryDeadline;
  private final Duration packingCheck;

  /**
   * {@code deliveryDeadline}: how long each fulfillment awaits delivery once it is shipped; {@code
   * packingCheck}: how long it awaits packing before it reads the Shipment again.
   */
  public FulfillmentService(
      FulfillmentWorkflows workflows, Duration deliveryDeadline, Duration packingCheck) {
    this.workflows = workflows;
    this.deliveryDeadline = deliveryDeadline;
    this.packingCheck = packingCheck;
  }

  @Override
  public void orderChanged(OrderChanged order) {
    if (!"PAID".equals(order.status())) {
      return;
    }
    if (order.shippingAddress() == null || order.shippingOption() == null) {
      log.info(
          "Order {} is paid without a Shipping address, as before Sprint 5: no fulfillment",
          order.orderId());
      return;
    }
    var started =
        workflows.start(
            new FulfillmentRequest(
                order.orderId(),
                order.customerId(),
                order.lines(),
                order.shippingAddress(),
                order.shippingOption(),
                deliveryDeadline,
                packingCheck,
                order.correlationId()));
    if (started) {
      log.info("Started Order {}'s fulfillment", order.orderId());
    } else {
      log.info("Order {}'s fulfillment was started already; nothing more", order.orderId());
    }
  }

  @Override
  public void shipmentChanged(String orderId, ShipmentChange change) {
    if (workflows.signal(orderId, change)) {
      log.info(
          "Signalled Order {}'s fulfillment: Shipment {} version {} is {}",
          orderId,
          change.shipmentId(),
          change.version(),
          change.status());
    } else {
      log.info(
          "Skipped Shipment {} version {}: no fulfillment is running for Order {}",
          change.shipmentId(),
          change.version(),
          orderId);
    }
  }
}
