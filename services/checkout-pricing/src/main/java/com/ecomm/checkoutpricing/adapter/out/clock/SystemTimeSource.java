package com.ecomm.checkoutpricing.adapter.out.clock;

import com.ecomm.checkoutpricing.application.port.out.TimeSource;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Reads the application's {@link Clock}, which tests replace to move time forward. */
@Component
class SystemTimeSource implements TimeSource {

  private final Clock clock;

  SystemTimeSource(Clock clock) {
    this.clock = clock;
  }

  @Override
  public Instant now() {
    return clock.instant();
  }
}
