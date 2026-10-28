package com.ecomm.template.adapter.out.clock;

import com.ecomm.template.application.port.out.TimeSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Ticks in microseconds, the precision Postgres stores, so what a service writes to Postgres reads
 * back with the time it answered.
 */
@Component
class SystemTimeSource implements TimeSource {

  private final Clock clock = Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));

  @Override
  public Instant now() {
    return clock.instant();
  }
}
