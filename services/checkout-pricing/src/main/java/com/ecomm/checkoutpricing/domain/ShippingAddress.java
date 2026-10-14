package com.ecomm.checkoutpricing.domain;

/**
 * Where a Customer's Order should be sent: the recipient's name, one or two lines, the postcode,
 * the city and an optional region, an ISO 3166-1 alpha-2 country, and an optional phone. As the
 * Customer enters it, any field may be missing or malformed; once the {@code AddressValidator} has
 * passed it, it is normalised, and an optional field it lacks is null.
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
