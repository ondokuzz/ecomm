package com.ecomm.inventory.adapter.in.web;

/** No Reservation has this ID, or not one owned by the Customer named. */
class ReservationNotFoundException extends RuntimeException {

  ReservationNotFoundException(String id) {
    super("No Reservation " + id);
  }
}
