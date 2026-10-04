package com.ecomm.ordermanagement.adapter.in.web;

/** A list was asked for a page that can't exist: a negative page, or a size out of range. */
class InvalidPageException extends RuntimeException {

  InvalidPageException(String message) {
    super(message);
  }
}
