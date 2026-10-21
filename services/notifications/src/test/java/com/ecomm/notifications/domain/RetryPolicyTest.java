package com.ecomm.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The dispatcher's backoff: six attempts over about an hour, each wait twice the one before. */
class RetryPolicyTest {

  private final RetryPolicy policy = new RetryPolicy(6, Duration.ofMinutes(2));

  @Test
  void waitsTwoFourEightSixteenAndThirtyTwoMinutesThenGivesUp() {
    assertThat(policy.retryAfter(1)).contains(Duration.ofMinutes(2));
    assertThat(policy.retryAfter(2)).contains(Duration.ofMinutes(4));
    assertThat(policy.retryAfter(3)).contains(Duration.ofMinutes(8));
    assertThat(policy.retryAfter(4)).contains(Duration.ofMinutes(16));
    assertThat(policy.retryAfter(5)).contains(Duration.ofMinutes(32));
    assertThat(policy.retryAfter(6)).isEmpty();
  }

  @Test
  void aSingleAttemptIsNeverRetried() {
    assertThat(new RetryPolicy(1, Duration.ofMinutes(2)).retryAfter(1)).isEmpty();
  }
}
