package com.ecomm.checkoutpricing.adapter.in.web;

/** The Customer has no live Checkout Session. */
class NoCurrentSessionException extends RuntimeException {

  NoCurrentSessionException() {
    super("You have no checkout in progress.");
  }
}
