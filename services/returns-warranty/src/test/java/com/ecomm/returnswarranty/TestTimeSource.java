package com.ecomm.returnswarranty;

import com.ecomm.returnswarranty.application.port.out.TimeSource;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Primary;

/**
 * A clock fixed at {@link #NOW}, so a test places a delivery inside or outside a window by its
 * distance from it. A test that needs time to pass sets it, and puts it back with {@link #reset()}.
 */
@Primary
class TestTimeSource implements TimeSource {

  static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

  private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

  @Override
  public Instant now() {
    return now.get();
  }

  void set(Instant instant) {
    now.set(instant);
  }

  void reset() {
    now.set(NOW);
  }
}
