package com.ecomm.recommendations.domain;

/**
 * A Product recommended with another, bought in {@code orders} counting Orders: with the other, or,
 * {@link Reason#POPULAR_IN_CATEGORY POPULAR_IN_CATEGORY}, at all, with the name of the {@code
 * category} it is popular in, which is null otherwise.
 */
public record Recommendation(String sku, long orders, Reason reason, String category) {}
