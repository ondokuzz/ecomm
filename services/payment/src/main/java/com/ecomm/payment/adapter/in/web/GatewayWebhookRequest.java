package com.ecomm.payment.adapter.in.web;

import com.ecomm.payment.domain.GatewayAuthorization;
import com.ecomm.payment.domain.GatewaySettlement;
import com.ecomm.payment.domain.InvalidPaymentException;

/**
 * A Gateway webhook's body: {@code {"eventId", "reference", "outcome": "APPROVED" | "DECLINED",
 * "declineReason"}}, the reason only for a decline. The values are taken raw so that a number is
 * rejected rather than coerced.
 */
record GatewayWebhookRequest(
    Object eventId, Object reference, Object outcome, Object declineReason) {

  GatewaySettlement toSettlement() {
    var reference = string("reference", this.reference);
    GatewayAuthorization answer =
        switch (string("outcome", outcome)) {
          case "APPROVED" -> new GatewayAuthorization.Approved(reference);
          case "DECLINED" ->
              new GatewayAuthorization.Declined(reference, string("declineReason", declineReason));
          case null, default ->
              throw new InvalidPaymentException("outcome must be APPROVED or DECLINED");
        };
    return new GatewaySettlement(string("eventId", eventId), answer);
  }

  private static String string(String name, Object value) {
    if (value != null && !(value instanceof String)) {
      throw new InvalidPaymentException(name + " must be a string");
    }
    return (String) value;
  }
}
