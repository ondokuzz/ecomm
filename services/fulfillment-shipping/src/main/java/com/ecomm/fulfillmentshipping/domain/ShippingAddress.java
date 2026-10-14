package com.ecomm.fulfillmentshipping.domain;

/**
 * Where a Shipment goes, as the Order was placed with it; Checkout validated and normalised it.
 *
 * @param country an ISO 3166-1 alpha-2 code, such as {@code DE}
 */
public record ShippingAddress(
    String recipientName,
    String line1,
    String line2,
    String postcode,
    String city,
    String region,
    String country,
    String phone) {}
