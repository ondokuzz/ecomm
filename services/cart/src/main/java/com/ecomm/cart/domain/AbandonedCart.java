package com.ecomm.cart.domain;

import java.time.Instant;

/**
 * A Cart that held Variants and went unchanged past the abandonment threshold. It is one
 * abandonment, told apart from the Customer's others by {@code lastChangedAt}: a change after it
 * makes the Cart a new candidate.
 */
public record AbandonedCart(String customerId, Cart cart, Instant lastChangedAt) {}
