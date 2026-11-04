package com.ecomm.supportassistant.adapter.out.clock;

import com.ecomm.supportassistant.application.port.out.TimeSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Ticks in milliseconds, the precision a message's time is shown in. */
@Component
class SystemTimeSource implements TimeSource {

  private final Clock clock = Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));

  @Override
  public Instant now() {
    return clock.instant();
  }
}
