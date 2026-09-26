package com.ecomm.payment.application.port.out;

import com.ecomm.payment.domain.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

  void add(Payment payment);

  Optional<Payment> find(UUID id);
}
