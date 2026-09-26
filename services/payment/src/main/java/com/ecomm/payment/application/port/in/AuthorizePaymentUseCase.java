package com.ecomm.payment.application.port.in;

import com.ecomm.payment.domain.AuthorizationRequest;
import com.ecomm.payment.domain.Payment;

public interface AuthorizePaymentUseCase {

  /** Asks the payment gateway to authorize the amount and records the outcome. */
  Payment authorize(AuthorizationRequest request);
}
