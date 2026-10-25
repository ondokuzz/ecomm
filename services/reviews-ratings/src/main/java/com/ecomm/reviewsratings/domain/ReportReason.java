package com.ecomm.reviewsratings.domain;

/** Why a Customer reports a review. */
public enum ReportReason {
  SPAM,
  OFFENSIVE,
  OFF_TOPIC,
  OTHER;

  /**
   * @throws InvalidReviewException unless {@code name} is one of them
   */
  public static ReportReason of(String name) {
    for (var reason : values()) {
      if (reason.name().equals(name)) {
        return reason;
      }
    }
    throw new InvalidReviewException("The reason must be SPAM, OFFENSIVE, OFF_TOPIC or OTHER.");
  }
}
