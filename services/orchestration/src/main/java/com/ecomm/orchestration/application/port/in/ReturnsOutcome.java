package com.ecomm.orchestration.application.port.in;

/**
 * How a run of the returns Saga ended: the Refund's Payment transaction, when it refunded; the
 * replacement Shipment, when it exchanged; and the reason it held the RMA, when the Refund was
 * refused.
 */
public record ReturnsOutcome(
    Status status, String refundReference, String replacementShipmentId, String holdReason) {

  public enum Status {
    /** The units are refunded, and restocked when they were returned. The RMA is resolved. */
    REFUNDED,
    /** A replacement is committed, shipped and dispatched. The RMA is resolved. */
    EXCHANGED,
    /** The Inspection refused it: nothing is restocked or refunded. The RMA is resolved. */
    CLOSED,
    /** Its units didn't come by the Send-back deadline. */
    EXPIRED,
    /**
     * The Refund was declined, or couldn't be made: the RMA is on hold. Restocking already done
     * stays. Resolving it is Staff's, and out of scope.
     */
    REFUND_REFUSED
  }
}
