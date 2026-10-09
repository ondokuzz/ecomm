package com.ecomm.orchestration.application.port.out;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import io.temporal.activity.ActivityInterface;

/**
 * Payment's commands. Authorizing takes an {@code Idempotency-Key} from the run; voiding needs
 * none.
 */
@ActivityInterface
public interface PaymentActivities {

  /** What the gateway answered: a Payment authorized, declined for a reason, or still pending. */
  record Authorization(String paymentId, Status status, String declineReason) {

    public enum Status {
      AUTHORIZED,
      DECLINED,
      PENDING
    }
  }

  Authorization authorizePayment(
      String customerId, String orderId, Amount amount, String paymentMethod);

  void voidPayment(String customerId, String paymentId);
}
