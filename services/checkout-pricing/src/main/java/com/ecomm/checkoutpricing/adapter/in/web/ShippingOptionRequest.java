package com.ecomm.checkoutpricing.adapter.in.web;

/**
 * The quoted Shipping option a Customer chooses: {@code {"optionId": "EXPRESS"}}. Taken raw so that
 * a number or an object is rejected rather than coerced.
 */
record ShippingOptionRequest(Object optionId) {

  /** The option ID of {@code request}, which may be missing altogether. */
  static String optionIdOf(ShippingOptionRequest request) {
    if (request == null || !(request.optionId() instanceof String optionId) || optionId.isBlank()) {
      throw new InvalidShippingRequestException("choosing a Shipping option needs its optionId");
    }
    return optionId;
  }
}
