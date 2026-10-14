package com.ecomm.checkoutpricing.adapter.in.web;

import com.ecomm.checkoutpricing.domain.FieldViolation;
import com.ecomm.checkoutpricing.domain.InvalidShippingAddressException;
import com.ecomm.checkoutpricing.domain.ShippingAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * A Shipping address as the Customer entered it: {@code {"recipientName", "line1", "line2"?,
 * "postcode", "city", "region"?, "country", "phone"?}}. Taken raw so that a field that isn't text
 * is named rather than coerced; whether the rest is valid is the {@code AddressValidator}'s call.
 */
record ShippingAddressRequest(
    Object recipientName,
    Object line1,
    Object line2,
    Object postcode,
    Object city,
    Object region,
    Object country,
    Object phone) {

  /** The address of {@code request}, which may be missing altogether. */
  static ShippingAddress addressOf(ShippingAddressRequest request) {
    if (request == null) {
      throw new InvalidShippingRequestException("setting a Shipping address needs the address");
    }
    var fields = new LinkedHashMap<String, Object>();
    fields.put("recipientName", request.recipientName());
    fields.put("line1", request.line1());
    fields.put("line2", request.line2());
    fields.put("postcode", request.postcode());
    fields.put("city", request.city());
    fields.put("region", request.region());
    fields.put("country", request.country());
    fields.put("phone", request.phone());
    var violations = new ArrayList<FieldViolation>();
    fields.forEach(
        (field, value) -> {
          if (value != null && !(value instanceof String)) {
            violations.add(new FieldViolation(field, "must be text"));
          }
        });
    if (!violations.isEmpty()) {
      throw new InvalidShippingAddressException(violations);
    }
    return new ShippingAddress(
        (String) request.recipientName(),
        (String) request.line1(),
        (String) request.line2(),
        (String) request.postcode(),
        (String) request.city(),
        (String) request.region(),
        (String) request.country(),
        (String) request.phone());
  }
}
