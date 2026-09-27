package com.ecomm.checkoutpricing.domain;

/** A Variant and how many of it the Customer's Cart holds. */
public record CartLine(String variantId, int quantity) {}
