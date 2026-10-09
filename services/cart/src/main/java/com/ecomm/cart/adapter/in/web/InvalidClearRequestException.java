package com.ecomm.cart.adapter.in.web;

/** Clearing a Customer's Cart was asked for without naming the Customer. */
class InvalidClearRequestException extends RuntimeException {

  InvalidClearRequestException(String message) {
    super(message);
  }
}
