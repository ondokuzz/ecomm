-- The ledger of Stock movements: one row per change to a Variant's On-hand units or to what
-- Reservations hold of them, written in the transaction that changes them. Rows are only ever
-- inserted. A Variant's on_hand is the sum of its on_hand_change. Like reservation_item, a movement
-- names its Variant without a foreign key, so the ledger outlives the Stock it describes.
CREATE TABLE stock_movement (
  id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  variant_id      VARCHAR(64)  NOT NULL,
  kind            VARCHAR(16)  NOT NULL
                  CHECK (kind IN ('ADJUSTED', 'RESERVED', 'RELEASED', 'COMMITTED', 'RECEIVED',
                                  'RESTOCKED')),
  on_hand_change  INTEGER      NOT NULL,
  reserved_change INTEGER      NOT NULL,
  reservation_id  UUID         REFERENCES reservation (id),
  reason          VARCHAR(200),
  at              TIMESTAMPTZ  NOT NULL
);

-- A Variant's movements are read newest first, and summed.
CREATE INDEX stock_movement_variant_id ON stock_movement (variant_id, id);

-- The version a Variant's Stock events carry. Every change takes the next value of one sequence,
-- rather than adding one to the row's, so a Variant that stops being stocked and is stocked again
-- still moves on from the version its removal was published with.
CREATE SEQUENCE stock_version;

ALTER TABLE stock ADD COLUMN version BIGINT;

UPDATE stock SET version = nextval('stock_version');

ALTER TABLE stock ALTER COLUMN version SET NOT NULL;

-- Opening balances: the Stock a stack already has, as if Staff had just set it, and what its
-- ACTIVE Reservations hold, expired or not, so the sweeper's release of those balances out too.
INSERT INTO stock_movement (variant_id, kind, on_hand_change, reserved_change, reason, at)
SELECT variant_id, 'ADJUSTED', on_hand, 0, 'opening balance', now()
FROM stock
ORDER BY variant_id;

INSERT INTO stock_movement
  (variant_id, kind, on_hand_change, reserved_change, reservation_id, reason, at)
SELECT i.variant_id, 'RESERVED', 0, i.quantity, r.id, 'opening balance', now()
FROM reservation r JOIN reservation_item i ON i.reservation_id = r.id
WHERE r.status = 'ACTIVE'
ORDER BY r.id, i.variant_id;

-- The Variants still to be published once with change BACKFILLED, so that consumers start
-- complete: every Variant stocked before Stock events. The startup job deletes each row as it
-- publishes it.
CREATE TABLE stock_awaiting_backfill_event (
  variant_id VARCHAR(64) PRIMARY KEY
);

INSERT INTO stock_awaiting_backfill_event (variant_id) SELECT variant_id FROM stock;
