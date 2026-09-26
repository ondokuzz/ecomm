package com.ecomm.payment.application.port.in;

import com.ecomm.payment.domain.Payment;
import java.util.Optional;
import java.util.UUID;

public interface FindPaymentUseCase {

  Optional<Payment> payment(UUID id);
}
