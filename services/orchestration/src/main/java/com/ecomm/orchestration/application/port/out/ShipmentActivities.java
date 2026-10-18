package com.ecomm.orchestration.application.port.out;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingAddress;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingOption;
import com.ecomm.orchestration.application.port.in.FulfillmentRequest.Line;
import io.temporal.activity.ActivityInterface;
import java.util.List;

/**
 * Fulfillment & Shipping's Shipment commands, each under an {@code Idempotency-Key} from the run.
 */
@ActivityInterface
public interface ShipmentActivities {

  /**
   * The Order's Shipment: its ID, its status, such as {@code AWAITING_PICK} or {@code ON_HOLD}, and
   * why it is held, null unless it is.
   */
  record Shipment(String id, String status, String holdReason) {}

  /**
   * Creates the Order's Shipment and returns it, awaiting pick. When the Order has one already,
   * that one, as it is now.
   */
  Shipment createShipment(
      String orderId,
      String customerId,
      List<Line> lines,
      ShippingAddress shippingAddress,
      ShippingOption shippingOption);

  /** Books the packed Shipment with its Carrier: it is {@code SHIPPED}. */
  void dispatchShipment(String shipmentId);

  /** Puts the packed Shipment {@code ON_HOLD}, for {@code reason}. */
  void holdShipment(String shipmentId, String reason);
}
