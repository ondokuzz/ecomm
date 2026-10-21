package com.ecomm.notifications.adapter.in.scheduling;

import com.ecomm.notifications.application.port.in.DispatchUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends the due {@code PENDING} Notifications every {@code ecomm.notifications.dispatch.poll-every}
 * (a second), a moment after their event is consumed. {@code
 * ecomm.notifications.dispatch.enabled=false} turns it off.
 */
@Component
@ConditionalOnProperty(
    name = "ecomm.notifications.dispatch.enabled",
    havingValue = "true",
    matchIfMissing = true)
class Dispatcher {

  private final DispatchUseCase dispatch;

  Dispatcher(DispatchUseCase dispatch) {
    this.dispatch = dispatch;
  }

  @Scheduled(fixedDelayString = "${ecomm.notifications.dispatch.poll-every}")
  void dispatchDue() {
    dispatch.dispatchDue();
  }
}
