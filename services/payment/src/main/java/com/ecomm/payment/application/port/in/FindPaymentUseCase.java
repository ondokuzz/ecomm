package com.ecomm.payment.application.port.in;

import com.ecomm.payment.domain.Payment;
import java.util.Optional;
import java.util.UUID;

public interface FindPaymentUseCase {

  /** Empty unless the Payment exists and belongs to this Customer. */
  Optional<Payment> payment(String customerId, UUID id);
}
