package com.ecomm.fulfillmentshipping.adapter.out.clock;

import com.ecomm.fulfillmentshipping.application.port.out.TimeSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/** The system clock, to the microsecond Postgres keeps, so that what is read back is what was. */
@Component
class SystemTimeSource implements TimeSource {

  @Override
  public Instant now() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }
}
