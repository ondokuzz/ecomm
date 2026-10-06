package com.ecomm.reviewsratings.adapter.out.clock;

import com.ecomm.reviewsratings.application.port.out.TimeSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Ticks in milliseconds, the precision Mongo stores a date in. */
@Component
class SystemTimeSource implements TimeSource {

  private final Clock clock = Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));

  @Override
  public Instant now() {
    return clock.instant();
  }
}
