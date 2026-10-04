-- An Order's Order Status history: every Status it has been in, oldest first, from its placement
-- on. Rows are only ever inserted. customer_order.status stays the current Status, and its version
-- goes up with every change, which is what stops two racing changes from both winning.
CREATE TABLE order_status_history (
  order_id   UUID        NOT NULL REFERENCES customer_order (id),
  position   INTEGER     NOT NULL CHECK (position >= 0),
  status     VARCHAR(16) NOT NULL,
  at         TIMESTAMPTZ NOT NULL,
  changed_by VARCHAR(16) NOT NULL,
  -- Reconstructed by this migration for an Order placed before histories were kept.
  backfilled BOOLEAN     NOT NULL,
  PRIMARY KEY (order_id, position)
);

ALTER TABLE customer_order ADD COLUMN version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1);

-- Sprints 1 and 2 kept neither change times nor callers, and only Checkout could place an Order or
-- change it. Each existing Order gets its placement, and, when it has moved on, its current Status,
-- marked backfilled, at its placement time too; whatever lay between is lost.
INSERT INTO order_status_history (order_id, position, status, at, changed_by, backfilled)
SELECT id, 0, 'PLACED', placed_at, 'CHECKOUT', FALSE FROM customer_order;

INSERT INTO order_status_history (order_id, position, status, at, changed_by, backfilled)
SELECT id, 1, status, placed_at, 'CHECKOUT', TRUE FROM customer_order WHERE status <> 'PLACED';

UPDATE customer_order SET version = 2 WHERE status <> 'PLACED';

ALTER TABLE customer_order ALTER COLUMN version DROP DEFAULT;

-- The Orders still to be published once with change BACKFILLED, so that consumers start complete:
-- every Order that existed before Order events. The startup job deletes each row as it publishes it.
CREATE TABLE order_awaiting_backfill_event (
  order_id UUID PRIMARY KEY REFERENCES customer_order (id)
);

INSERT INTO order_awaiting_backfill_event (order_id) SELECT id FROM customer_order;
