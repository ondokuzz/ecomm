package com.ecomm.payment.domain;

import com.ecomm.commons.money.Money;
import java.util.UUID;

/**
 * An Order's payment as recorded after the gateway answered: {@code AUTHORIZED}, or {@code
 * DECLINED} with the gateway's {@code declineReason}, which is null otherwise. It belongs to the
 * Customer it was authorized for, and only they may see it.
 */
public record Payment(
    UUID id,
    String customerId,
    String orderId,
    Money amount,
    PaymentStatus status,
    String declineReason,
    String gatewayReference) {

  /** The Payment the gateway's answer to {@code request} makes. */
  public static Payment of(UUID id, AuthorizationRequest request, GatewayAuthorization answer) {
    var declineReason =
        answer instanceof GatewayAuthorization.Declined declined ? declined.reason() : null;
    return new Payment(
        id,
        request.customerId(),
        request.orderId(),
        request.amount(),
        declineReason == null ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED,
        declineReason,
        answer.reference());
  }

  public boolean belongsTo(String customerId) {
    return this.customerId.equals(customerId);
  }
}
