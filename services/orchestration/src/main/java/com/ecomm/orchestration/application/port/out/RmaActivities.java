package com.ecomm.orchestration.application.port.out;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import io.temporal.activity.ActivityInterface;

/**
 * Returns & Warranty's read of an RMA, and its commands for the returns Saga, each under an {@code
 * Idempotency-Key} from the run.
 */
@ActivityInterface
public interface RmaActivities {

  /**
   * The RMA as it is now: its status, its Send-back deadline (an ISO instant, null until approved),
   * its Refund amount (null until its Order is projected), and its Inspection's outcome and note
   * once inspected.
   */
  record Rma(
      String status,
      String sendBackBy,
      Amount refundAmount,
      String inspectionOutcome,
      String inspectionNote) {}

  /** Whether {@code expire} expired the RMA, or found its units received meanwhile. */
  enum Expiry {
    EXPIRED,
    /** Returns & Warranty refused it (409): the units were received before the timer fired. */
    RECEIVED_MEANWHILE
  }

  Rma findRma(String rmaId);

  Expiry expireRma(String rmaId);

  /**
   * Resolves the RMA {@code REFUNDED}, {@code EXCHANGED} or {@code CLOSED}, naming the Refund's
   * Payment transaction or the replacement Shipment where it is one, and answers whether the
   * Order's every delivered unit is now refunded.
   */
  boolean resolveRma(
      String rmaId,
      String resolution,
      String paymentTransactionReference,
      String replacementShipmentId,
      String note);

  /** Puts the RMA {@code ON_HOLD}, for {@code reason}. */
  void holdRma(String rmaId, String reason);
}
