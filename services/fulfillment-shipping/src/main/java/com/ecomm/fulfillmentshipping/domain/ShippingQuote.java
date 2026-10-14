package com.ecomm.fulfillmentshipping.domain;

import java.util.List;

/** The Warehouse an Order to a destination would ship from, and the Shipping options from it. */
public record ShippingQuote(Warehouse origin, List<ShippingOption> options) {}
