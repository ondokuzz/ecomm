package com.ecomm.orchestration.application.port.in;

/**
 * How a run of the checkout Saga ended: the Order it placed, if it got that far, and the gateway's
 * reason when the Payment was declined.
 */
public record CheckoutOutcome(Status status, String orderId, String declineReason) {

  public enum Status {
    /** The Order is paid; its Stock is committed. */
    PAID,
    /**
     * The gateway declined the Payment; the Order is cancelled, and the session can be paid again.
     */
    DECLINED,
    /**
     * The Reservation no longer held the Stock: the Payment is voided and the Order cancelled, so
     * nothing was charged.
     */
    HOLD_EXPIRED,
    /** A step failed for good; whatever it left behind is undone. */
    FAILED
  }
}
