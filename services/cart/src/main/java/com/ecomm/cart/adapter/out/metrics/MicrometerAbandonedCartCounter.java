package com.ecomm.cart.adapter.out.metrics;

import com.ecomm.cart.application.port.out.AbandonedCartCounter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * {@code cart_abandoned_total}, registered at 0 on start so Prometheus has a sample before the
 * first abandonment, and Grafana's {@code increase()} counts it.
 */
@Component
class MicrometerAbandonedCartCounter implements AbandonedCartCounter {

  private final Counter abandoned;

  MicrometerAbandonedCartCounter(MeterRegistry registry) {
    this.abandoned =
        Counter.builder("cart.abandoned")
            .description("Carts marked abandoned: idle past ecomm.cart.abandoned-after")
            .register(registry);
  }

  @Override
  public void increment() {
    abandoned.increment();
  }
}
