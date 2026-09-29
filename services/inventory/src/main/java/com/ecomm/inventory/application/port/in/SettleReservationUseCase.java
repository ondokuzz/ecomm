package com.ecomm.inventory.application.port.in;

import com.ecomm.inventory.domain.Reservation;
import java.util.Optional;
import java.util.UUID;

/** Ends a Reservation. Both calls are empty unless it exists and belongs to this Customer. */
public interface SettleReservationUseCase {

  /**
   * Takes the Reservation's Stock off on-hand for good; committing it again changes nothing. Throws
   * {@code ReservationExpiredException} or {@code ReservationReleasedException} once it no longer
   * holds its Stock.
   */
  Optional<Reservation> commit(String customerId, UUID id);

  /**
   * Gives the Reservation's Stock back; releasing it again changes nothing. Throws {@code
   * ReservationCommittedException} once it has been committed.
   */
  Optional<Reservation> release(String customerId, UUID id);
}
