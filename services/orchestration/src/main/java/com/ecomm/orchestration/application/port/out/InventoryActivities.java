package com.ecomm.orchestration.application.port.out;

import io.temporal.activity.ActivityInterface;

/** Inventory's Reservations. Committing one again changes nothing, so it takes no key. */
@ActivityInterface
public interface InventoryActivities {

  /** Whether the Reservation's Stock was committed, or the Reservation no longer held it. */
  enum Commitment {
    COMMITTED,
    /** It expired, or was released: its Stock may already be someone else's. */
    LAPSED
  }

  Commitment commitReservation(String customerId, String reservationId);
}
