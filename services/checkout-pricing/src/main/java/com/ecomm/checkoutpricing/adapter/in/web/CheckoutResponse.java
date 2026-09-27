package com.ecomm.checkoutpricing.adapter.in.web;

import com.ecomm.checkoutpricing.domain.CheckoutResult;

record CheckoutResponse(String orderId, String status) {

  static CheckoutResponse of(CheckoutResult result) {
    return new CheckoutResponse(result.orderId(), result.status().name());
  }
}
