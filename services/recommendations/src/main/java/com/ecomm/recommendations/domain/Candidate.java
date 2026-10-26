package com.ecomm.recommendations.domain;

import java.time.Instant;

/**
 * A Product that may be recommended: bought in {@code orders} counting Orders, the latest placed at
 * {@code latestOrder}, either with another Product or at all, as one of its Category's best
 * sellers.
 */
public record Candidate(String sku, long orders, Instant latestOrder) {}
