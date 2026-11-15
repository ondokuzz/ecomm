package com.ecomm.notifications.adapter.out.metrics;

import com.ecomm.notifications.application.port.out.CartReminderCounter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@code notifications_cart_reminders_total{outcome}}, each outcome registered at 0 on start so
 * Prometheus has a sample before the first abandonment, and Grafana's {@code increase()} counts it.
 */
@Component
class MicrometerCartReminderCounter implements CartReminderCounter {

  private final Map<Outcome, Counter> counters = new EnumMap<>(Outcome.class);

  MicrometerCartReminderCounter(MeterRegistry registry) {
    for (var outcome : Outcome.values()) {
      counters.put(
          outcome,
          Counter.builder("notifications.cart.reminders")
              .description("What came of each Abandoned Cart: a Cart reminder sent, or skipped")
              .tag("outcome", label(outcome))
              .register(registry));
    }
  }

  /** As the {@code outcome} label reads it, such as {@code skippedOptedOut}. */
  private static String label(Outcome outcome) {
    return switch (outcome) {
      case SENT -> "sent";
      case SKIPPED_OPTED_OUT -> "skippedOptedOut";
      case SKIPPED_CHANGED -> "skippedChanged";
      case SKIPPED_EMPTY -> "skippedEmpty";
    };
  }

  @Override
  public void count(Outcome outcome) {
    counters.get(outcome).increment();
  }
}
