package com.ecomm.checkoutpricing.domain;

/**
 * One attempt to pay a Checkout Session, a run of the checkout Saga: the Customer who paid, how it
 * stands, the Order it placed once it got that far, and the gateway's reason when the Payment was
 * declined. A session may be paid again after a decline; the latest attempt is the one that counts.
 */
public record PaymentAttempt(
    String customerId, Status status, String orderId, String declineReason) {

  public enum Status {
    /** The Saga is still going. */
    PROCESSING,
    /** The Order is paid. */
    PAID,
    /** The gateway declined the Payment; the session can be paid again. */
    DECLINED,
    /**
     * The Reservation stopped holding the Stock: nothing was charged, and the Order is cancelled.
     */
    HOLD_EXPIRED,
    /** A step failed for good; nothing was charged. */
    FAILED
  }
}
