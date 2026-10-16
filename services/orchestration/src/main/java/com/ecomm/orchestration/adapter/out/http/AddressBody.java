package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingAddress;
import com.fasterxml.jackson.annotation.JsonInclude;

/** A Shipping address in a command's body; its optional fields are left out when it has none. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record AddressBody(
    String recipientName,
    String line1,
    String line2,
    String postcode,
    String city,
    String region,
    String country,
    String phone) {

  static AddressBody of(ShippingAddress a) {
    return a == null
        ? null
        : new AddressBody(
            a.recipientName(),
            a.line1(),
            a.line2(),
            a.postcode(),
            a.city(),
            a.region(),
            a.country(),
            a.phone());
  }
}
