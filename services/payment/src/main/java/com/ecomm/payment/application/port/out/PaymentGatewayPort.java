package com.ecomm.payment.application.port.out;

import com.ecomm.payment.domain.AuthorizationRequest;
import com.ecomm.payment.domain.GatewayAuthorization;

/**
 * The payment gateway, kept behind a port so a real, market-specific gateway can replace the mock
 * without touching the use cases (ADR 0005).
 */
public interface PaymentGatewayPort {

  GatewayAuthorization authorize(AuthorizationRequest request);
}
