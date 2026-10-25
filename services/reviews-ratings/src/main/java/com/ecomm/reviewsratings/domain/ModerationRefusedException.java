package com.ecomm.reviewsratings.domain;

/** A moderation action the review's status, or another Staff member's action first, rules out. */
public class ModerationRefusedException extends RuntimeException {

  private final ModerationRefusal refusal;

  public ModerationRefusedException(ModerationRefusal refusal) {
    super(refusal.message());
    this.refusal = refusal;
  }

  public ModerationRefusal refusal() {
    return refusal;
  }
}
