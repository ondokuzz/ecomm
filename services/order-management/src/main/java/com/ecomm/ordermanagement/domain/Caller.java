package com.ecomm.ordermanagement.domain;

/**
 * Who changed an Order: the service identity that called Order Management. Only Checkout does in
 * Sprint 3; Orchestration joins it with the Sagas.
 */
public enum Caller {
  CHECKOUT
}
