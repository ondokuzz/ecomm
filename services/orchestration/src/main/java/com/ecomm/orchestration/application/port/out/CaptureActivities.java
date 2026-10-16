package com.ecomm.orchestration.application.port.out;

import io.temporal.activity.ActivityInterface;

/** Payment's Capture, under an {@code Idempotency-Key} from the run. */
@ActivityInterface
public interface CaptureActivities {

  /** What the gateway answered: the Payment captured, or its capture declined for a reason. */
  record Capture(String paymentId, Status status, String declineReason) {

    public enum Status {
      CAPTURED,
      DECLINED
    }
  }

  /**
   * Captures the Order's authorized Payment, found through Payment's read by Order. One captured
   * already is answered as captured. With no Payment to capture, it fails as {@link
   * Refusals#REFUSED}.
   */
  Capture capturePayment(String customerId, String orderId);
}
