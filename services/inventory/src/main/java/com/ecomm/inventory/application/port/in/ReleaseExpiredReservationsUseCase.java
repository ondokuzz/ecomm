package com.ecomm.inventory.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Housekeeping: marks expired {@code ACTIVE} Reservations released. They already hold no Stock, so
 * nothing anyone sees depends on when this runs.
 */
public interface ReleaseExpiredReservationsUseCase {

  /** The IDs of the Reservations it released. */
  List<UUID> releaseExpired();
}
