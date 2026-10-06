package com.ecomm.reviewsratings.domain;

/** Why a Customer may not post a review of a Product, by the code the API answers with. */
public enum RefusalReason {
  /** They have no Order for any of its Variants that counts. */
  NOT_PURCHASED("notPurchased", "Only a Customer who has paid for this Product may review it."),
  /** They have reviewed it already, and may edit or delete that review instead. */
  ALREADY_REVIEWED("alreadyReviewed", "You have already reviewed this Product.");

  private final String code;
  private final String message;

  RefusalReason(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public String code() {
    return code;
  }

  /** What to tell the Customer. */
  public String message() {
    return message;
  }
}
