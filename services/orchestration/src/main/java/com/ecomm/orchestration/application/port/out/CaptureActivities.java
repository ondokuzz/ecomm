package com.ecomm.orchestration.application.port.out;

import io.temporal.activity.ActivityInterface;

/** Payment's read by Order, and its Capture under an {@code Idempotency-Key} from the run. */
@ActivityInterface
public interface CaptureActivities {

  /** The Order's Payment to capture: its ID, and whether it is captured already. */
  record PaymentToCapture(String id, boolean captured) {}

  /** What the gateway answered: the Payment captured, or its capture declined for a reason. */
  record Capture(String paymentId, Status status, String declineReason) {

    public enum Status {
      CAPTURED,
      DECLINED
    }
  }

  /**
   * The Order's authorized Payment, or the one captured already, through Payment's read by Order.
   * With neither, it fails as {@link Refusals#REFUSED}.
   */
  PaymentToCapture findPayment(String customerId, String orderId);

  /** Captures the Payment. One captured already is answered as captured. */
  Capture capturePayment(String customerId, String paymentId);
}
