package com.ecomm.ordermanagement.adapter.out.clock;

import com.ecomm.ordermanagement.application.port.out.TimeSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Ticks in microseconds, the precision Postgres stores, so an Order reads back with the same {@code
 * placedAt} it was placed with.
 */
@Component
class SystemTimeSource implements TimeSource {

  private final Clock clock = Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));

  @Override
  public Instant now() {
    return clock.instant();
  }
}
