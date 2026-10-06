package com.ecomm.reviewsratings.domain;

import java.util.Optional;

/**
 * Whether a Customer may post a review of a Product, and, when they may, the Variant they bought;
 * when they may not, why. A Customer who has reviewed it already gets their review back.
 */
public record Eligibility(
    Optional<String> variantBought, Optional<RefusalReason> refusal, Optional<Review> review) {

  /**
   * Their own review comes first: once posted it stays, whatever happens to its Order. Without one,
   * they need a Variant bought in an Order that counts.
   */
  public static Eligibility of(Optional<Review> existing, Optional<String> variantBought) {
    if (existing.isPresent()) {
      return new Eligibility(
          Optional.empty(), Optional.of(RefusalReason.ALREADY_REVIEWED), existing);
    }
    if (variantBought.isEmpty()) {
      return new Eligibility(
          Optional.empty(), Optional.of(RefusalReason.NOT_PURCHASED), Optional.empty());
    }
    return new Eligibility(variantBought, Optional.empty(), Optional.empty());
  }

  public boolean eligible() {
    return refusal.isEmpty();
  }
}
