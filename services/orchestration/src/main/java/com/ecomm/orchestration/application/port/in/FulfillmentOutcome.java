package com.ecomm.orchestration.application.port.in;

/**
 * How a run of the fulfillment Saga ended: the Order's Shipment, once created, and the reason it
 * was put on hold when the Payment's capture was refused.
 */
public record FulfillmentOutcome(Status status, String shipmentId, String holdReason) {

  public enum Status {
    /** The Payment is captured, the Shipment delivered, and the Order {@code DELIVERED}. */
    DELIVERED,
    /**
     * The Payment couldn't be captured: the Shipment is on hold, the Order stays {@code PAID} and
     * the Payment {@code AUTHORIZED}. Resolving it is Staff's, and out of scope.
     */
    CAPTURE_REFUSED,
    /** The Order is {@code SHIPPED}, but the Carrier never confirmed delivery by the deadline. */
    DELIVERY_UNCONFIRMED,
    /** Fulfillment & Shipping refused to create the Shipment; nothing was charged. */
    FAILED
  }
}
