package com.ecomm.notifications.application.port.in;

/** Sends the {@code PENDING} Notifications that are due. */
public interface DispatchUseCase {

  /** Tries each one due now, until none is; how many were tried. */
  int dispatchDue();
}
