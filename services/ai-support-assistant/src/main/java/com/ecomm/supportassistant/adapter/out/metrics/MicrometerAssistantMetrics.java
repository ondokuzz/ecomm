package com.ecomm.supportassistant.adapter.out.metrics;

import com.ecomm.supportassistant.application.port.out.AssistantMetrics;
import com.ecomm.supportassistant.application.port.out.SupportModel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@code support_assistant_tokens_total} by kind, {@code support_assistant_turn_duration_seconds}
 * by outcome and {@code support_assistant_limit_refusals_total} by limit, each registered at 0 on
 * start so Prometheus has a sample before the first, and Grafana's {@code increase()} counts it.
 */
@Component
class MicrometerAssistantMetrics implements AssistantMetrics {

  private final Counter input;
  private final Counter output;
  private final Counter cacheRead;
  private final Map<Outcome, Timer> turns = new EnumMap<>(Outcome.class);
  private final Map<Limit, Counter> refusals = new EnumMap<>(Limit.class);

  MicrometerAssistantMetrics(MeterRegistry registry) {
    input = tokens(registry, "input");
    output = tokens(registry, "output");
    cacheRead = tokens(registry, "cache_read");
    for (var outcome : Outcome.values()) {
      turns.put(
          outcome,
          Timer.builder("support_assistant.turn.duration")
              .description("How long a Customer's message took to answer, by how its turn ended")
              .tag("outcome", name(outcome))
              // For the dashboard's p95, as http.server.requests has.
              .publishPercentileHistogram()
              .register(registry));
    }
    for (var limit : Limit.values()) {
      refusals.put(
          limit,
          Counter.builder("support_assistant.limit.refusals")
              .description("Messages refused by a Customer's limit, or turns by the ceiling")
              .tag("limit", name(limit))
              .register(registry));
    }
  }

  private static Counter tokens(MeterRegistry registry, String kind) {
    return Counter.builder("support_assistant.tokens")
        .description("Tokens the provider's answers took, by kind")
        .tag("kind", kind)
        .register(registry);
  }

  @Override
  public void turn(Outcome outcome, Duration took) {
    turns.get(outcome).record(took);
  }

  @Override
  public void tokens(SupportModel.Usage usage) {
    input.increment(usage.input());
    output.increment(usage.output());
    cacheRead.increment(usage.cacheRead());
  }

  @Override
  public void refused(Limit limit) {
    refusals.get(limit).increment();
  }

  private static String name(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }
}
