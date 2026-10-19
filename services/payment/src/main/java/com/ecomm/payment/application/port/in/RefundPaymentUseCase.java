package com.ecomm.payment.application.port.in;

import com.ecomm.commons.money.Money;
import com.ecomm.payment.domain.Payment;
import com.ecomm.payment.domain.PaymentGatewayUnavailableException;
import com.ecomm.payment.domain.PaymentNotRefundableException;
import com.ecomm.payment.domain.RefundExceedsCapturedException;
import java.util.Optional;
import java.util.UUID;

public interface RefundPaymentUseCase {

  /** What a Refund did to the Payment. */
  enum RefundOutcome {
    /** The gateway gave the amount back. */
    REFUNDED,
    /** The gateway refused: its reason is recorded, and the Payment is as it was. */
    DECLINED
  }

  /**
   * The Payment as the Refund left it, what the Refund did, and the gateway's reason when it
   * declined.
   */
  record Refund(Payment payment, RefundOutcome outcome, String declineReason) {}

  /**
   * Gives {@code amount} of the Customer's captured Payment back through the gateway, passing it
   * {@code idempotencyKey}, and records the gateway's answer as a {@code REFUND} transaction for
   * {@code refundFor}, such as an RMA reference. Empty unless the Payment exists and belongs to
   * this Customer.
   *
   * @throws PaymentNotRefundableException unless the Payment is captured or partly refunded
   * @throws RefundExceedsCapturedException when {@code amount} is more than is left to refund, or
   *     in another Currency; nothing is asked of the gateway
   * @throws PaymentGatewayUnavailableException when the gateway fails to answer; nothing is
   *     recorded
   */
  Optional<Refund> refund(
      String customerId, UUID id, Money amount, String refundFor, String idempotencyKey);
}
