package com.ecomm.payment.adapter.out.gateway;

import com.ecomm.payment.application.port.out.PaymentGatewayPort;
import com.ecomm.payment.domain.AuthorizationRequest;
import com.ecomm.payment.domain.GatewayAuthorization;
import com.ecomm.payment.domain.PaymentGatewayUnavailableException;
import java.util.UUID;

/**
 * Stands in for a real gateway, reading the payment method as a test token, each answer with a
 * fresh {@code mock-} reference: {@code tok_approve} authorizes; {@code tok_decline} and {@code
 * tok_insufficient_funds} decline, as {@code card_declined} and {@code insufficient_funds}; {@code
 * tok_gateway_error} fails to answer; any other token declines as {@code unknown_payment_method}.
 */
class MockPaymentGatewayAdapter implements PaymentGatewayPort {

  @Override
  public GatewayAuthorization authorize(AuthorizationRequest request) {
    var reference = "mock-" + UUID.randomUUID();
    return switch (request.paymentMethod()) {
      case "tok_approve" -> new GatewayAuthorization.Approved(reference);
      case "tok_decline" -> new GatewayAuthorization.Declined(reference, "card_declined");
      case "tok_insufficient_funds" ->
          new GatewayAuthorization.Declined(reference, "insufficient_funds");
      case "tok_gateway_error" ->
          throw new PaymentGatewayUnavailableException("The mock gateway failed, as asked");
      default -> new GatewayAuthorization.Declined(reference, "unknown_payment_method");
    };
  }
}
