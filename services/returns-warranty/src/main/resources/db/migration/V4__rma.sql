-- An RMA: a Customer's request to return units of one Variant of a delivered Order, or to claim
-- their warranty. version goes up by one with every change, under the row's lock.
CREATE TABLE rma (
  id               UUID          PRIMARY KEY,
  version          BIGINT        NOT NULL CHECK (version >= 1),
  customer_id      VARCHAR(255)  NOT NULL,
  order_id         VARCHAR(64)   NOT NULL,
  variant_id       VARCHAR(64)   NOT NULL,
  product_name     VARCHAR(255)  NOT NULL,
  quantity         INTEGER       NOT NULL CHECK (quantity >= 1),
  serial_numbers   TEXT[]        NOT NULL,
  kind             VARCHAR(16)   NOT NULL,
  reason           VARCHAR(1000) NOT NULL,
  status           VARCHAR(16)   NOT NULL,
  requested_at     TIMESTAMPTZ   NOT NULL,
  -- The window it was checked against, kept so later Catalog edits don't change it.
  window_kind      VARCHAR(16)   NOT NULL,
  window_starts_at TIMESTAMPTZ   NOT NULL,
  window_ends_at   TIMESTAMPTZ   NOT NULL,
  -- The Warranty terms a Warranty Window was worked out from; null for a Return Period.
  warranty_months       INTEGER,
  warranty_manufacturer VARCHAR(255)
);

-- Staff's RMA approval queue: by status, oldest first. A Customer's RMAs, and an Order's.
CREATE INDEX rma_queue_idx ON rma (status, requested_at, id);
CREATE INDEX rma_by_customer_idx ON rma (customer_id, requested_at, id);
CREATE INDEX rma_by_order_idx ON rma (order_id);

-- Each RMA decision, in order. Rows are only ever inserted, in the transaction of the decision.
CREATE TABLE rma_decision (
  rma_id     UUID          NOT NULL REFERENCES rma (id),
  position   INTEGER       NOT NULL CHECK (position >= 0),
  status     VARCHAR(16)   NOT NULL,
  decided_by VARCHAR(255)  NOT NULL,
  decided_at TIMESTAMPTZ   NOT NULL,
  note       VARCHAR(1000),
  PRIMARY KEY (rma_id, position)
);
