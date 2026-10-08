package com.ecomm.payment.application.port.in;

import com.ecomm.payment.domain.Payment;
import com.ecomm.payment.domain.PaymentGatewayUnavailableException;
import com.ecomm.payment.domain.PaymentNotVoidableException;
import java.util.Optional;
import java.util.UUID;

public interface VoidPaymentUseCase {

  /**
   * Releases the Customer's authorized or pending Payment through the gateway and records a {@code
   * VOID} transaction. A Payment that is already voided is returned as it is, so voiding is
   * naturally idempotent. Empty unless the Payment exists and belongs to this Customer.
   *
   * @throws PaymentNotVoidableException when the Payment was declined
   * @throws PaymentGatewayUnavailableException when the gateway fails to answer; nothing is
   *     recorded
   */
  Optional<Payment> voidPayment(String customerId, UUID id);
}
