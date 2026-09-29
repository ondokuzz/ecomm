package com.ecomm.inventory;

import com.ecomm.inventory.application.port.out.TimeSource;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Primary;

/**
 * A clock that stands still until a test moves it, and only ever forward, since every test class
 * shares it. It starts at the real time, in microseconds like Postgres.
 */
@Primary
class TestTimeSource implements TimeSource {

  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.now().truncatedTo(ChronoUnit.MICROS));

  @Override
  public Instant now() {
    return now.get();
  }

  void advanceTo(Instant instant) {
    now.updateAndGet(
        current -> {
          if (instant.isBefore(current)) {
            throw new IllegalArgumentException("The test clock only moves forward");
          }
          return instant;
        });
  }

  void advanceBy(Duration duration) {
    advanceTo(now().plus(duration));
  }
}
