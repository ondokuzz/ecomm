package com.ecomm.inventory.domain;

import java.util.UUID;

/** The Reservation was committed, so its Stock is no longer held. */
public class ReservationCommittedException extends RuntimeException {

  private final UUID reservationId;

  public ReservationCommittedException(UUID reservationId) {
    super("Reservation " + reservationId + " has been committed, so it can no longer be released");
    this.reservationId = reservationId;
  }

  public UUID reservationId() {
    return reservationId;
  }
}
