package com.ecomm.cart.adapter.out.clock;

import com.ecomm.cart.application.port.out.TimeSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Ticks in milliseconds, the precision a Cart's last change is kept in. */
@Component
class SystemTimeSource implements TimeSource {

  private final Clock clock = Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));

  @Override
  public Instant now() {
    return clock.instant();
  }
}
