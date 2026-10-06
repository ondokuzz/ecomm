package com.ecomm.reviewsratings.domain;

/** A review the Customer may not post, for {@code reason}. */
public class ReviewRefusedException extends RuntimeException {

  private final RefusalReason reason;

  public ReviewRefusedException(RefusalReason reason) {
    super(reason.message());
    this.reason = reason;
  }

  public RefusalReason reason() {
    return reason;
  }
}
