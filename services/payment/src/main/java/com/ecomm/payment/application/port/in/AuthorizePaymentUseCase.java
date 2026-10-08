package com.ecomm.payment.application.port.in;

import com.ecomm.payment.domain.AuthorizationRequest;
import com.ecomm.payment.domain.Payment;
import com.ecomm.payment.domain.PaymentGatewayUnavailableException;

public interface AuthorizePaymentUseCase {

  /**
   * Asks the payment gateway to authorize the amount, passing it {@code idempotencyKey}, and
   * records its answer, approved or declined, as the Customer's Payment with its authorization
   * transaction.
   *
   * @throws PaymentGatewayUnavailableException when the gateway fails to answer; nothing is
   *     recorded
   */
  Payment authorize(AuthorizationRequest request, String idempotencyKey);
}
