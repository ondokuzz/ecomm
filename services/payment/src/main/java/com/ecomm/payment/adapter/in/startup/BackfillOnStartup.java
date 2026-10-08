package com.ecomm.payment.adapter.in.startup;

import com.ecomm.payment.application.port.in.PublishBackfillUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Publishes the Payments that existed before Payment events once the service is up. After the first
 * start that finds any, there are none left, so it does nothing.
 */
@Component
class BackfillOnStartup {

  private static final Logger log = LoggerFactory.getLogger(BackfillOnStartup.class);

  private final PublishBackfillUseCase backfill;

  BackfillOnStartup(PublishBackfillUseCase backfill) {
    this.backfill = backfill;
  }

  @EventListener(ApplicationReadyEvent.class)
  void publishBackfill() {
    var published = backfill.publishBackfill();
    if (published > 0) {
      log.info("Published {} existing Payments as backfilled", published);
    }
  }
}
