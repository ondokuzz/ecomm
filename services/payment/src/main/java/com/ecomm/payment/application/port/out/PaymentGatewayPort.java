package com.ecomm.payment.application.port.out;

import com.ecomm.payment.domain.AuthorizationRequest;
import com.ecomm.payment.domain.GatewayAuthorization;
import com.ecomm.payment.domain.PaymentGatewayUnavailableException;

/**
 * The payment gateway, kept behind a port so a real, market-specific gateway can replace the mock
 * without touching the use cases (ADR 0005). Every call carries an idempotency key: the gateway
 * answers a repeated key with its first answer, so a retried call never authorizes or voids twice.
 */
public interface PaymentGatewayPort {

  /**
   * The gateway's answer: approved or declined.
   *
   * @throws PaymentGatewayUnavailableException when the gateway fails to answer
   */
  GatewayAuthorization authorize(AuthorizationRequest request, String idempotencyKey);

  /**
   * Releases the authorization the gateway knows by {@code authorizationReference}, before capture;
   * returns the gateway's reference for the void.
   *
   * @throws PaymentGatewayUnavailableException when the gateway fails to answer
   */
  String voidAuthorization(String authorizationReference, String idempotencyKey);
}
