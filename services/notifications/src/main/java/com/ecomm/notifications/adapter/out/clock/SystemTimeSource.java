package com.ecomm.notifications.adapter.out.clock;

import com.ecomm.notifications.application.port.out.TimeSource;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/** The system clock, to the microsecond Postgres keeps, so that what is read back is what was. */
@Component
class SystemTimeSource implements TimeSource {

  private final Clock clock = Clock.systemUTC();

  @Override
  public Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
