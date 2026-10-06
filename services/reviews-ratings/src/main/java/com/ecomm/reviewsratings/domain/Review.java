package com.ecomm.reviewsratings.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * A Customer's review of a Product: what they said, the Variant they bought, and their display name
 * as it was when they posted it. A Customer has at most one per Product. {@code editedAt} is empty
 * until they first edit it.
 */
public record Review(
    String id,
    String sku,
    String customerId,
    String author,
    String variantId,
    ReviewContent content,
    Instant createdAt,
    Optional<Instant> editedAt) {

  /** The same review saying {@code content} instead, edited {@code at}. */
  public Review edit(ReviewContent content, Instant at) {
    return new Review(id, sku, customerId, author, variantId, content, createdAt, Optional.of(at));
  }
}
