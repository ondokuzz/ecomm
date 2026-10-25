package com.ecomm.reviewsratings.adapter.in.web;

import com.ecomm.reviewsratings.domain.Review;
import java.time.Instant;

/**
 * A review as anyone may read it: its author by display name only, never by Customer ID. {@code
 * title} and {@code editedAt} are null when it has none, or was never edited. Anyone sees only a
 * {@code PUBLISHED} one; its author sees theirs whatever its {@code status}, and why Staff removed
 * it in {@code removalReason}.
 */
record ReviewResponse(
    String id,
    String sku,
    String author,
    String variantId,
    int rating,
    String title,
    String body,
    Instant createdAt,
    Instant editedAt,
    String status,
    String removalReason) {

  static ReviewResponse of(Review review) {
    var content = review.content();
    return new ReviewResponse(
        review.id(),
        review.sku(),
        review.author(),
        review.variantId(),
        content.rating(),
        content.title().orElse(null),
        content.body(),
        review.createdAt(),
        review.editedAt().orElse(null),
        review.status().name(),
        review.removalReason().orElse(null));
  }
}
