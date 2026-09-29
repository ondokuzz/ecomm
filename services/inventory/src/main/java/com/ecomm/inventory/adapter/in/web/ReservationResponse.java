package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.domain.Reservation;
import java.time.Instant;
import java.util.List;

record ReservationResponse(
    String id, String customerId, String status, Instant expiresAt, List<ItemBody> items) {

  static ReservationResponse of(Reservation reservation) {
    return new ReservationResponse(
        reservation.id().toString(),
        reservation.customerId(),
        reservation.status().name(),
        reservation.expiresAt(),
        reservation.items().lines().stream()
            .map(line -> new ItemBody(line.variantId(), line.quantity()))
            .toList());
  }
}
