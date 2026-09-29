package com.ecomm.inventory.domain;

import java.util.UUID;

/** The Reservation expired before it was committed. */
public class ReservationExpiredException extends RuntimeException {

  private final UUID reservationId;

  public ReservationExpiredException(UUID reservationId) {
    super("Reservation " + reservationId + " has expired, so it can no longer be committed");
    this.reservationId = reservationId;
  }

  public UUID reservationId() {
    return reservationId;
  }
}
