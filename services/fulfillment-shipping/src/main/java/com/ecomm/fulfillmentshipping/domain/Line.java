package com.ecomm.fulfillmentshipping.domain;

/** A quantity of one Variant to ship. */
public record Line(String variantId, int quantity) {}
