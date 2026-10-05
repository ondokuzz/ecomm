package com.ecomm.ordermanagement.adapter.in.web;

/** Staff asked for Orders by a filter that can't match anything as written. */
class InvalidFilterException extends RuntimeException {

  InvalidFilterException(String message) {
    super(message);
  }
}
