package com.ecomm.supportassistant.application.port.in;

import java.time.Duration;

/**
 * The service has asked the provider as often as it may this minute, across every Customer: the
 * turn can be tried again once {@code retryAfter} has passed.
 */
public class AssistantBusyException extends RuntimeException {

  private final Duration retryAfter;

  public AssistantBusyException(Duration retryAfter) {
    super("The Support Assistant is busy; try again in " + retryAfter.toSeconds() + " seconds");
    this.retryAfter = retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
