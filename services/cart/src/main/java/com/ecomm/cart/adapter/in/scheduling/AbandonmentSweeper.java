package com.ecomm.cart.adapter.in.scheduling;

import com.ecomm.cart.application.port.in.DetectAbandonedCartsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every {@code ecomm.cart.abandonment-sweep} (5 minutes by default), sweeps for Abandoned Carts. A
 * sweep that fails, with Kafka down say, is logged and tried again on the next. Tests turn it off
 * with {@code ecomm.cart.abandonment-sweeper.enabled=false} and sweep when they choose.
 */
@Component
@ConditionalOnProperty(
    name = "ecomm.cart.abandonment-sweeper.enabled",
    havingValue = "true",
    matchIfMissing = true)
class AbandonmentSweeper {

  private static final Logger log = LoggerFactory.getLogger(AbandonmentSweeper.class);

  private final DetectAbandonedCartsUseCase abandonment;

  AbandonmentSweeper(DetectAbandonedCartsUseCase abandonment) {
    this.abandonment = abandonment;
  }

  @Scheduled(
      fixedDelayString = "${ecomm.cart.abandonment-sweep:5m}",
      initialDelayString = "${ecomm.cart.abandonment-sweep:5m}")
  void sweep() {
    try {
      var abandoned = abandonment.sweep();
      if (abandoned > 0) {
        log.info("Marked {} Carts abandoned", abandoned);
      }
    } catch (RuntimeException e) {
      log.warn("The abandonment sweep failed; the next one tries again", e);
    }
  }
}
