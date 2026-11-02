package com.ecomm.payment.adapter.in.web;

/**
 * The Payment transaction history was asked for a page that can't exist, or with a bound that isn't
 * an instant.
 */
class InvalidHistoryRequestException extends RuntimeException {

  InvalidHistoryRequestException(String message) {
    super(message);
  }
}
