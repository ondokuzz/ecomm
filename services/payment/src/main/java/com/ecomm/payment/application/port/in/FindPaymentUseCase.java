package com.ecomm.payment.application.port.in;

import com.ecomm.payment.domain.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FindPaymentUseCase {

  /** Empty unless the Payment exists and belongs to this Customer. */
  Optional<Payment> payment(String customerId, UUID id);

  /**
   * The Customer's Payments for the Order, newest first; empty when the Order isn't theirs, so the
   * answer never reveals someone else's.
   */
  List<Payment> payments(String customerId, String orderId);
}
