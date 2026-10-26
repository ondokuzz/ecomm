package com.ecomm.returnswarranty.adapter.out.clock;

import com.ecomm.returnswarranty.application.port.out.TimeSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Ticks in microseconds, the precision Postgres stores, so an RMA reads back with the times it
 * answered when it was requested or decided.
 */
@Component
class SystemTimeSource implements TimeSource {

  private final Clock clock = Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));

  @Override
  public Instant now() {
    return clock.instant();
  }
}
