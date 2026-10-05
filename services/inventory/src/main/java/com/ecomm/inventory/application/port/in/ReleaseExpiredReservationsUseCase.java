package com.ecomm.inventory.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Marks expired {@code ACTIVE} Reservations released, recording their {@code RELEASED} movements
 * and publishing their Variants' Stock. They already hold no Stock, so what callers read doesn't
 * depend on when this runs; only the events, and the ledger, wait for it.
 */
public interface ReleaseExpiredReservationsUseCase {

  /** The IDs of the Reservations it released. */
  List<UUID> releaseExpired();
}
