package com.ecomm.inventory.domain;

/** Where a Reservation is: holding Stock, turned into a permanent decrement, or given back. */
public enum ReservationStatus {
  ACTIVE,
  COMMITTED,
  RELEASED
}
