package com.ecomm.notifications.adapter.out.clock;

import com.ecomm.notifications.application.port.out.TimeSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Ticks in microseconds, the precision Postgres stores, so what is read back is what was answered.
 */
@Component
class SystemTimeSource implements TimeSource {

  private final Clock clock = Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));

  @Override
  public Instant now() {
    return clock.instant();
  }
}
