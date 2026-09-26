package com.ecomm.payment.domain;

import com.ecomm.commons.money.Money;
import java.util.UUID;

/** An Order's payment as recorded after the gateway answered. */
public record Payment(
    UUID id, String orderId, Money amount, PaymentStatus status, String gatewayReference) {

  public static Payment authorized(
      UUID id, AuthorizationRequest request, GatewayAuthorization authorization) {
    return new Payment(
        id,
        request.orderId(),
        request.amount(),
        PaymentStatus.AUTHORIZED,
        authorization.reference());
  }
}
