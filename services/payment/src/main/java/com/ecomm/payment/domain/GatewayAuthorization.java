package com.ecomm.payment.domain;

/**
 * What a payment gateway answered, identified by the gateway's own reference for it: it approved
 * the amount, or declined it for a reason, such as {@code insufficient_funds}.
 */
public sealed interface GatewayAuthorization {

  String reference();

  record Approved(String reference) implements GatewayAuthorization {}

  record Declined(String reference, String reason) implements GatewayAuthorization {}
}
