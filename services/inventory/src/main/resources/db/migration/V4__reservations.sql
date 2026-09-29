-- Stock is now on-hand units; Reservations hold some of them. Available Stock is on-hand less what
-- ACTIVE Reservations that haven't expired yet hold.
ALTER TABLE stock RENAME COLUMN quantity TO on_hand;

CREATE TABLE reservation (
  id          UUID         PRIMARY KEY,
  customer_id VARCHAR(255) NOT NULL,
  status      VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED')),
  expires_at  TIMESTAMPTZ  NOT NULL
);

-- The sweeper looks for ACTIVE Reservations by expiry.
CREATE INDEX reservation_active_expires_at ON reservation (expires_at) WHERE status = 'ACTIVE';

CREATE TABLE reservation_item (
  reservation_id UUID        NOT NULL REFERENCES reservation (id),
  variant_id     VARCHAR(64) NOT NULL REFERENCES stock (variant_id),
  quantity       INTEGER     NOT NULL CHECK (quantity > 0),
  PRIMARY KEY (reservation_id, variant_id)
);

-- What a Variant's Reservations hold is looked up by Variant.
CREATE INDEX reservation_item_variant_id ON reservation_item (variant_id);
