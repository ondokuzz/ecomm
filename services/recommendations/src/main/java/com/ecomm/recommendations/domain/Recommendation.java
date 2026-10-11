package com.ecomm.recommendations.domain;

/** A Product bought together with another, in {@code orders} counting Orders. */
public record Recommendation(String sku, long orders) {}
