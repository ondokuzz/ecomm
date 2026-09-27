package com.ecomm.ordermanagement.adapter.in.web;

/** No Order has this ID, or not one the caller may see. */
class OrderNotFoundException extends RuntimeException {

  OrderNotFoundException(String id) {
    super("No order " + id);
  }
}
