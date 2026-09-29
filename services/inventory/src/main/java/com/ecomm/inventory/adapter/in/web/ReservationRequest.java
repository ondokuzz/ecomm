package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.domain.StockBatch;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A Reservation for a Customer until {@code expiresAt}: {@code {"customerId", "expiresAt", "items":
 * [{"variantId", "quantity"}]}}.
 */
record ReservationRequest(String customerId, Instant expiresAt, List<ItemBody> items) {

  StockBatch toItems() {
    return ItemBody.toBatch(items);
  }

  /** To the microsecond, the precision Postgres stores, so it reads back as it was sent. */
  Instant toExpiresAt() {
    return expiresAt == null ? null : expiresAt.truncatedTo(ChronoUnit.MICROS);
  }
}
