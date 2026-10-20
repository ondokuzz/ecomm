-- What a Stock movement was for, beyond its Reservation: the RMA reference a restock names, or the
-- owner, rma:<rmaId>, of the Reservation for a replacement. Null for every movement before.
ALTER TABLE stock_movement ADD COLUMN reference VARCHAR(255);

-- An RMA has at most one Reservation not yet released, for its replacement, so a retried reserve
-- answers the first. One that expired uncommitted is released before the RMA holds Stock again.
-- Customers' Reservations are owned by their Customer ID and may be many.
CREATE UNIQUE INDEX reservation_one_per_rma ON reservation (customer_id)
  WHERE customer_id LIKE 'rma:%' AND status <> 'RELEASED';

-- service-commons' idempotency keys (@IdempotentCommand): a row per key a caller sent with a command
-- that succeeded, written in the command's transaction, with the request it came with and the
-- response it got. A repeat is answered from here. Rows are pruned once older than 7 days.
CREATE TABLE idempotency_key (
  caller            TEXT                     NOT NULL,
  key               VARCHAR(255)             NOT NULL,
  method            TEXT                     NOT NULL,
  path              TEXT                     NOT NULL,
  body_hash         TEXT                     NOT NULL,
  response_status   INT,
  response_type     TEXT,
  response_location TEXT,
  response_body     BYTEA,
  created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (caller, key)
);

CREATE INDEX idempotency_key_by_created_at_idx ON idempotency_key (created_at);
