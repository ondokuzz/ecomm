package com.ecomm.supportassistant.application.port.out;

import java.time.Duration;

/** What the assistant costs and how its turns end, for operators to watch. */
public interface AssistantMetrics {

  /** How a turn ended. */
  enum Outcome {
    /** The provider replied. */
    REPLIED,
    /** It reached its bounds, in provider calls or time, before the provider replied. */
    BOUNDED,
    /** The provider failed, or the circuit breaker refused to ask it. */
    UNAVAILABLE,
    /** The service had asked the provider as often as it may. */
    BUSY,
    /** Anything else went wrong. */
    FAILED
  }

  /** A limit a message or a provider call was refused by. */
  enum Limit {
    MESSAGES_PER_HOUR,
    MESSAGES_PER_DAY,
    PROVIDER_CALLS_PER_MINUTE
  }

  void turn(Outcome outcome, Duration took);

  /** The tokens one provider answer took. */
  void tokens(SupportModel.Usage usage);

  void refused(Limit limit);
}
