package com.ecomm.supportassistant.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Counts what one subject, such as a Customer, does against limits over rolling windows, shared by
 * every instance of the service.
 */
public interface RateLimiter {

  /** At most {@code count} in any {@code per}. */
  record Limit(int count, Duration per) {}

  /**
   * The limit that one more would pass, and how long until it wouldn't.
   *
   * @param limit the one of the limits passed that frees up last
   */
  record Refusal(Limit limit, Duration retryAfter) {}

  /**
   * Counts one more for {@code subject} at {@code at}, unless that would pass one of {@code
   * limits}: then it counts nothing and answers why.
   */
  Optional<Refusal> take(String subject, List<Limit> limits, Instant at);
}
