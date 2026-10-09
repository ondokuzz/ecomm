package com.ecomm.orchestration.application.port.out;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import io.temporal.activity.ActivityInterface;

/**
 * Payment's commands, and the read the Saga awaits a pending authorization's settlement with.
 * Authorizing takes an {@code Idempotency-Key} from the run; voiding needs none.
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

  /**
   * The Payment once its bank has settled its pending authorization: authorized, or declined for a
   * reason. While it is still pending, the activity fails as {@link #SETTLEMENT_PENDING}, to be
   * read again after a backoff until the Saga's deadline.
   */
  Authorization awaitSettlement(String customerId, String paymentId);

  /** The failure type of a read that found the Payment still pending. */
  String SETTLEMENT_PENDING = "SettlementPending";

  void voidPayment(String customerId, String paymentId);
}
