package com.ecomm.payment.adapter.out.gateway;

import com.ecomm.payment.application.port.out.PaymentGatewayPort;
import com.ecomm.payment.domain.AuthorizationRequest;
import com.ecomm.payment.domain.GatewayAuthorization;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Stands in for a real gateway: authorizes every request, with a fresh {@code mock-} reference. */
@Component
class MockPaymentGatewayAdapter implements PaymentGatewayPort {

  @Override
  public GatewayAuthorization authorize(AuthorizationRequest request) {
    return new GatewayAuthorization("mock-" + UUID.randomUUID());
  }
}
