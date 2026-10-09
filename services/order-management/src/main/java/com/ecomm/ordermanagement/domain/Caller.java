package com.ecomm.ordermanagement.domain;

/**
 * Who changed an Order: the service identity that called Order Management. Checkout did until the
 * checkout Saga; Orchestration does for the Saga's steps.
 */
public enum Caller {
  CHECKOUT,
  ORCHESTRATION
}
