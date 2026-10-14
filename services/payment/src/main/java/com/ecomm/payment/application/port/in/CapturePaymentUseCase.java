package com.ecomm.payment.application.port.in;

import com.ecomm.payment.domain.Payment;
import com.ecomm.payment.domain.PaymentGatewayUnavailableException;
import com.ecomm.payment.domain.PaymentNotCapturableException;
import java.util.Optional;
import java.util.UUID;

public interface CapturePaymentUseCase {

  /** What a capture did to the Payment. */
  enum CaptureOutcome {
    /** The gateway charged the authorized amount, now or in an earlier capture. */
    CAPTURED,
    /** The gateway refused the charge: its reason is recorded, and the Payment stays authorized. */
    DECLINED
  }

  /**
   * The Payment as the capture left it, what the capture did, and the gateway's reason when it
   * declined.
   */
  record Capture(Payment payment, CaptureOutcome outcome, String declineReason) {}

  /**
   * Charges the Customer's authorized Payment for its full amount through the gateway, passing it
   * {@code idempotencyKey}, and records the gateway's answer as a {@code CAPTURE} transaction. A
   * Payment that is already captured is returned as it is, so capturing is naturally idempotent.
   * Empty unless the Payment exists and belongs to this Customer.
   *
   * @throws PaymentNotCapturableException when the Payment is pending, declined or voided
   * @throws PaymentGatewayUnavailableException when the gateway fails to answer; nothing is
   *     recorded
   */
  Optional<Capture> capture(String customerId, UUID id, String idempotencyKey);
}
