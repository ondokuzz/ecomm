package com.ecomm.payment.application;

import com.ecomm.payment.application.port.in.AuthorizePaymentUseCase;
import com.ecomm.payment.application.port.in.FindPaymentUseCase;
import com.ecomm.payment.application.port.out.PaymentGatewayPort;
import com.ecomm.payment.application.port.out.PaymentRepository;
import com.ecomm.payment.domain.AuthorizationRequest;
import com.ecomm.payment.domain.Payment;
import java.util.Optional;
import java.util.UUID;

public class PaymentService implements AuthorizePaymentUseCase, FindPaymentUseCase {

  private final PaymentGatewayPort gateway;
  private final PaymentRepository payments;

  public PaymentService(PaymentGatewayPort gateway, PaymentRepository payments) {
    this.gateway = gateway;
    this.payments = payments;
  }

  @Override
  public Payment authorize(AuthorizationRequest request) {
    var payment = Payment.authorized(UUID.randomUUID(), request, gateway.authorize(request));
    payments.add(payment);
    return payment;
  }

  @Override
  public Optional<Payment> payment(String customerId, UUID id) {
    return payments.find(id).filter(payment -> payment.belongsTo(customerId));
  }
}
