package com.ecomm.reviewsratings.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * A Customer's report of another Customer's review, with an optional note of up to 500 characters,
 * trimmed, a blank one being none. It is open until Staff publish, remove or dismiss the reports of
 * the review it is of, which resolves it.
 */
public record Report(
    String customerId,
    ReportReason reason,
    Optional<String> note,
    Instant reportedAt,
    boolean resolved) {

  public static final int MAX_NOTE = 500;

  public Report {
    note = note.map(String::strip).filter(n -> !n.isEmpty());
    if (note.isPresent() && note.get().length() > MAX_NOTE) {
      throw new InvalidReviewException("The note may have at most " + MAX_NOTE + " characters.");
    }
  }

  Report resolve() {
    return new Report(customerId, reason, note, reportedAt, true);
  }
}
