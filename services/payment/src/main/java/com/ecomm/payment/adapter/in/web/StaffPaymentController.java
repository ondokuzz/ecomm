package com.ecomm.payment.adapter.in.web;

import com.ecomm.payment.application.port.in.BrowsePaymentsUseCase;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff read any Customer's Payments with a {@code STAFF} token, to answer a question about a
 * charge or reconcile it against the gateway. Staff can't change a Payment.
 */
@RestController
@RequestMapping("/staff/payments")
@PreAuthorize("hasRole('STAFF')")
class StaffPaymentController {

  private final BrowsePaymentsUseCase browse;

  StaffPaymentController(BrowsePaymentsUseCase browse) {
    this.browse = browse;
  }

  /** Every Payment for the Order, newest first, each with its transactions. */
  @GetMapping
  List<PaymentResponse> payments(@RequestParam String orderId) {
    return browse.paymentsForOrder(orderId).stream().map(PaymentResponse::of).toList();
  }
}
