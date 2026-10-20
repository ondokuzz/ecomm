package com.ecomm.returnswarranty;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Test-only: RMAs' rows as Sprint 5 wrote them, with only the columns it had, at version 2. */
final class Sprint5Rmas {

  private Sprint5Rmas() {}

  static void insert(
      JdbcClient jdbc,
      UUID id,
      String status,
      Instant windowEndsAt,
      String orderId,
      String variantId) {
    jdbc.sql(
            """
            INSERT INTO rma (id, version, customer_id, order_id, variant_id, product_name, quantity,
              serial_numbers, kind, reason, status, requested_at, window_kind, window_starts_at,
              window_ends_at)
            VALUES (:id, 2, :customerId, :orderId, :variantId, 'Charger', 1, '{}', 'RETURN',
              'Broken', :status, :requestedAt, 'RETURN_PERIOD', :startsAt, :endsAt)
            """)
        .param("id", id)
        .param("customerId", ReturnsWarrantyApiTest.CUSTOMER_ID)
        .param("orderId", orderId)
        .param("variantId", variantId)
        .param("status", status)
        .param("requestedAt", Timestamp.from(Instant.parse("2026-09-30T12:00:00Z")))
        .param("startsAt", Timestamp.from(Instant.parse("2026-09-29T12:00:00Z")))
        .param("endsAt", Timestamp.from(windowEndsAt))
        .update();
  }
}
