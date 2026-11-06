package com.ecomm.supportassistant.application.port.in;

import java.time.Duration;

/**
 * The Customer has sent as many messages as they may this hour or this day: they may ask again once
 * {@code retryAfter} has passed. Nothing was asked of the provider.
 */
public class AssistantRateLimitedException extends RuntimeException {

  private final Duration retryAfter;

  public AssistantRateLimitedException(Duration retryAfter) {
    super("You've sent a lot of messages; ask again in " + retryAfter.toSeconds() + " seconds");
    this.retryAfter = retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
