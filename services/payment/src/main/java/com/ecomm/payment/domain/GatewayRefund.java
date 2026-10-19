package com.ecomm.payment.domain;

/**
 * What a payment gateway answered to a Refund, identified by the gateway's own reference for it: it
 * gave the amount back, or declined to, for a reason such as {@code refund_window_closed}.
 */
public sealed interface GatewayRefund {

  String reference();

  record Approved(String reference) implements GatewayRefund {}

  record Declined(String reference, String reason) implements GatewayRefund {}
}
