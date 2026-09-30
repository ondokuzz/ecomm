package com.ecomm.promotions.adapter.out.clock;

import com.ecomm.promotions.application.port.out.TimeSource;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
class SystemTimeSource implements TimeSource {

  private final Clock clock = Clock.systemUTC();

  @Override
  public Instant now() {
    return clock.instant();
  }
}
