package com.ecomm.orchestration.application.port.out;

import io.temporal.activity.ActivityInterface;

/**
 * Fulfillment & Shipping's replacement Shipments, each command under an {@code Idempotency-Key}
 * from the run.
 */
@ActivityInterface
public interface ReplacementActivities {

  /** An RMA's replacement Shipment: its ID and status, such as {@code AWAITING_PICK}. */
  record Replacement(String id, String status) {}

  /**
   * Creates the RMA's replacement, of {@code quantity} of the Variant, to the Order Shipment's
   * address with its Shipping option, and returns it awaiting pick. When the RMA has one already,
   * that one, as it is now.
   */
  Replacement createReplacement(
      String rmaId, String orderId, String customerId, String variantId, int quantity);

  /** The RMA's replacement as it is now, read by RMA. */
  Replacement findReplacement(String rmaId);

  /** Books the packed replacement with its Carrier: it is {@code SHIPPED}. */
  void dispatchReplacement(String shipmentId);
}
