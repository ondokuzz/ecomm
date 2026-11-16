package com.ecomm.checkoutpricing.adapter.out.metrics;

import com.ecomm.checkoutpricing.application.port.out.CheckoutFunnel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * {@code checkout_sessions_total{step}}, each step registered at 0 on start so Prometheus has a
 * sample before the first session, and Grafana's {@code increase()} counts it. The steps a session
 * has been counted at are a Redis hash, {@code checkout-funnel:<session ID>}, so each replica
 * counts it there once between them. It is kept for a day after its latest step, well past the
 * session's own lifetime and any read of how it was paid.
 */
@Component
class MicrometerCheckoutFunnel implements CheckoutFunnel {

  private static final Logger log = LoggerFactory.getLogger(MicrometerCheckoutFunnel.class);

  static final Duration KEPT = Duration.ofDays(1);

  private static final RedisScript<Long> FIRST_TIME =
      RedisScript.of(
          """
          if redis.call('HSETNX', KEYS[1], ARGV[1], 1) == 0 then return 0 end
          redis.call('PEXPIRE', KEYS[1], ARGV[2])
          return 1
          """,
          Long.class);

  private final StringRedisTemplate redis;
  private final Map<Step, Counter> counters = new EnumMap<>(Step.class);

  MicrometerCheckoutFunnel(StringRedisTemplate redis, MeterRegistry registry) {
    this.redis = redis;
    for (var step : Step.values()) {
      counters.put(
          step,
          Counter.builder("checkout.sessions")
              .description("Checkout Sessions that reached each step, each counted once per step")
              .tag("step", label(step))
              .register(registry));
    }
  }

  /** As the {@code step} label reads it, such as {@code optionChosen}. */
  private static String label(Step step) {
    return switch (step) {
      case STARTED -> "started";
      case ADDRESSED -> "addressed";
      case OPTION_CHOSEN -> "optionChosen";
      case PAY_ATTEMPTED -> "payAttempted";
      case PAID -> "paid";
      case DECLINED -> "declined";
      case HOLD_EXPIRED -> "holdExpired";
      case EXPIRED -> "expired";
    };
  }

  /** Uncounted, rather than failing the Customer's step, when Redis can't be reached. */
  @Override
  public void reached(String sessionId, Step step) {
    Long first;
    try {
      first =
          redis.execute(
              FIRST_TIME,
              List.of("checkout-funnel:" + sessionId),
              label(step),
              String.valueOf(KEPT.toMillis()));
    } catch (DataAccessException e) {
      log.warn("Couldn't count Checkout Session {} at {}", sessionId, label(step), e);
      return;
    }
    if (first != null && first == 1) {
      counters.get(step).increment();
    }
  }
}
