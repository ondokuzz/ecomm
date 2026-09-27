package com.ecomm.payment.domain;

import com.ecomm.commons.money.Money;
import java.util.UUID;

/**
 * An Order's payment as recorded after the gateway answered. It belongs to the Customer it was
 * authorized for, and only they may see it.
 */
public record Payment(
    UUID id,
    String customerId,
    String orderId,
    Money amount,
    PaymentStatus status,
    String gatewayReference) {

  public static Payment authorized(
      UUID id, AuthorizationRequest request, GatewayAuthorization authorization) {
    return new Payment(
        id,
        request.customerId(),
        request.orderId(),
        request.amount(),
        PaymentStatus.AUTHORIZED,
        authorization.reference());
  }

  public boolean belongsTo(String customerId) {
    return this.customerId.equals(customerId);
  }
}
