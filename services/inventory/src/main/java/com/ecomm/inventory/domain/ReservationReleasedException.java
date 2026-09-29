package com.ecomm.inventory.domain;

import java.util.UUID;

/** The Reservation was released before it was committed. */
public class ReservationReleasedException extends RuntimeException {

  private final UUID reservationId;

  public ReservationReleasedException(UUID reservationId) {
    super("Reservation " + reservationId + " has been released, so it can no longer be committed");
    this.reservationId = reservationId;
  }

  public UUID reservationId() {
    return reservationId;
  }
}
