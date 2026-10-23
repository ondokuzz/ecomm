package com.ecomm.notifications.domain;

/**
 * The content of a message about one step of an RMA: about its Order and the RMA, to its Customer.
 */
public sealed interface RmaMessage extends MessageContent
    permits RmaRequested, RmaApproved, RmaRejected, RmaReceived, RmaClosed, RmaExpired {

  RmaSummary rma();

  @Override
  default String customerId() {
    return rma().customerId();
  }

  @Override
  default String orderId() {
    return rma().orderId();
  }

  @Override
  default String rmaId() {
    return rma().rmaId();
  }
}
