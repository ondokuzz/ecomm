package com.ecomm.returnswarranty;

import com.ecomm.returnswarranty.application.port.out.TimeSource;
import java.time.Instant;
import org.springframework.context.annotation.Primary;

/**
 * A clock fixed at {@link #NOW}, so a test places a delivery inside or outside a window by its
 * distance from it.
 */
@Primary
class TestTimeSource implements TimeSource {

  static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

  @Override
  public Instant now() {
    return NOW;
  }
}
