package com.ecomm.notifications.domain;

import java.time.Duration;
import java.util.Optional;

/**
 * How a Notification whose sending failed transiently is tried again: at most {@code attempts}
 * times in all, the second {@code firstRetryAfter} after the first, each wait twice the one before.
 * Six attempts from two minutes wait 2, 4, 8, 16 and 32 minutes: about an hour.
 */
public record RetryPolicy(int attempts, Duration firstRetryAfter) {

  public RetryPolicy {
    if (attempts < 1) {
      throw new IllegalArgumentException("attempts must be at least 1");
    }
  }

  /** How long after attempt {@code made} (from 1) to try again, or empty when it was the last. */
  public Optional<Duration> retryAfter(int made) {
    if (made >= attempts) {
      return Optional.empty();
    }
    return Optional.of(firstRetryAfter.multipliedBy(1L << (made - 1)));
  }
}
