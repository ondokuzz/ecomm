package com.ecomm.orchestration.application.port.out;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import io.temporal.activity.ActivityInterface;

/** Payment's read by Order, and its Refund under an {@code Idempotency-Key} from the run. */
@ActivityInterface
public interface RefundActivities {

  /**
   * The Order's Payment to refund: its ID, and the gateway reference of a Refund already made of it
   * for the RMA reference, null when none is.
   */
  record PaymentToRefund(String id, String refundReference) {}

  /** What the gateway answered: the Refund's reference, or its decline reason. */
  record Refund(Status status, String reference, String declineReason) {

    public enum Status {
      REFUNDED,
      DECLINED
    }
  }

  /**
   * The Order's captured or partly refunded Payment, or the one refunded already for {@code
   * reference}, through Payment's read by Order. With none, it fails as {@link Refusals#REFUSED}.
   */
  PaymentToRefund findRefundablePayment(String customerId, String orderId, String reference);

  /** Refunds {@code amount} of the Payment for the RMA {@code reference}. */
  Refund refundPayment(String customerId, String paymentId, Amount amount, String reference);
}
