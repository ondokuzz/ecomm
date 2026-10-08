package com.ecomm.payment.application.port.in;

import com.ecomm.payment.domain.Payment;
import java.util.List;

/** Staff's reads: any Customer's Payments. */
public interface BrowsePaymentsUseCase {

  /** Every Payment for the Order, newest first. */
  List<Payment> paymentsForOrder(String orderId);
}
