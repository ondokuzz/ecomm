package com.ecomm.inventory.adapter.in.scheduling;

import com.ecomm.inventory.application.port.in.ReleaseExpiredReservationsUseCase;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every 30 seconds, marks expired {@code ACTIVE} Reservations released. Housekeeping only: they
 * stopped holding Stock the moment they expired. Tests turn it off with {@code
 * ecomm.inventory.reservation-sweeper.enabled=false} and sweep when they choose.
 */
@Component
@ConditionalOnProperty(
    name = "ecomm.inventory.reservation-sweeper.enabled",
    havingValue = "true",
    matchIfMissing = true)
class ReservationSweeper {

  private static final Logger log = LoggerFactory.getLogger(ReservationSweeper.class);

  private final ReleaseExpiredReservationsUseCase releaseExpired;

  ReservationSweeper(ReleaseExpiredReservationsUseCase releaseExpired) {
    this.releaseExpired = releaseExpired;
  }

  @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
  void sweep() {
    var released = releaseExpired.releaseExpired();
    if (!released.isEmpty()) {
      log.info("Released {} expired Reservations", released.size());
    }
  }
}
