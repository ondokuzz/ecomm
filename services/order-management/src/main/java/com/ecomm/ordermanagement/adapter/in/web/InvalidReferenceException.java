package com.ecomm.ordermanagement.adapter.in.web;

/** A Customer looked for an Order by something that isn't an Order reference. */
class InvalidReferenceException extends RuntimeException {

  InvalidReferenceException(String message) {
    super(message);
  }
}
