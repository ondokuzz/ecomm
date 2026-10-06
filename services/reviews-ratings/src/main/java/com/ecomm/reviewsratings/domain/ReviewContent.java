package com.ecomm.reviewsratings.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * What a Customer says in a review: a rating from 1 to 5, an optional title of up to 120
 * characters, and a body of up to 2,000. Both are trimmed, and a blank title is none.
 */
public record ReviewContent(int rating, Optional<String> title, String body) {

  public static final int MAX_TITLE = 120;
  public static final int MAX_BODY = 2_000;
  public static final String INVALID_RATING = "The rating must be from 1 to 5.";

  public ReviewContent {
    if (rating < 1 || rating > 5) {
      throw new InvalidReviewException(INVALID_RATING);
    }
    title = title.map(String::strip).filter(t -> !t.isEmpty());
    if (title.isPresent() && title.get().length() > MAX_TITLE) {
      throw new InvalidReviewException("The title may have at most " + MAX_TITLE + " characters.");
    }
    body = body == null ? "" : body.strip();
    if (body.isEmpty()) {
      throw new InvalidReviewException("The body is required.");
    }
    if (body.length() > MAX_BODY) {
      throw new InvalidReviewException(
          String.format(Locale.ROOT, "The body may have at most %,d characters.", MAX_BODY));
    }
  }

  /** From a request, where a missing title is {@code null}. */
  public static ReviewContent of(int rating, String title, String body) {
    return new ReviewContent(rating, Optional.ofNullable(title), body);
  }
}
