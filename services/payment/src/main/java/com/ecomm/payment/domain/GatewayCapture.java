package com.ecomm.payment.domain;

/**
 * What a payment gateway answered to a capture, identified by the gateway's own reference for it:
 * it charged the authorized amount, or declined to, for a reason such as {@code
 * authorization_expired}.
 */
public sealed interface GatewayCapture {

  String reference();

  record Approved(String reference) implements GatewayCapture {}

  record Declined(String reference, String reason) implements GatewayCapture {}
}
