package com.ecomm.inventory.domain;

/** Where a Reservation is: holding Stock, taken off on-hand for good, or given back. */
public enum ReservationStatus {
  ACTIVE,
  COMMITTED,
  RELEASED
}
