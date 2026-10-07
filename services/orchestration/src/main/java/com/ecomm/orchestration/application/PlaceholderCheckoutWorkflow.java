package com.ecomm.orchestration.application;

import com.ecomm.orchestration.application.port.in.CheckoutWorkflow;

/** Finishes at once; #60 replaces it with the checkout Saga's steps. */
public class PlaceholderCheckoutWorkflow implements CheckoutWorkflow {

  @Override
  public String checkout(String checkoutSessionId) {
    return checkoutSessionId;
  }
}
