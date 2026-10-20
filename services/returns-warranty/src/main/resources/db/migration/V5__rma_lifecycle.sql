-- The RMA lifecycle, from receipt to resolution (Sprint 6).

-- The Warehouse each Shipment ships from, where its RMAs' units go back. Null on a Shipment kept
-- before it was projected: the service replays the Shipment topic from the start while any is.
ALTER TABLE shipment ADD COLUMN warehouse_id VARCHAR(64);

-- Order Management's Orders, projected from order-management.order (group returns-warranty.orders)
-- for RMAs' Refund amounts: each line's Price, and the Order's Discounts and Tax, in its Currency.
CREATE TABLE customer_order (
  id              VARCHAR(64) PRIMARY KEY,
  version         BIGINT      NOT NULL,
  currency        CHAR(3)     NOT NULL,
  discounts_minor BIGINT      NOT NULL CHECK (discounts_minor >= 0),
  tax_minor       BIGINT      NOT NULL CHECK (tax_minor >= 0)
);

CREATE TABLE customer_order_line (
  order_id         VARCHAR(64) NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
  position         INTEGER     NOT NULL CHECK (position >= 0),
  variant_id       VARCHAR(64) NOT NULL,
  quantity         INTEGER     NOT NULL CHECK (quantity >= 1),
  unit_price_minor BIGINT      NOT NULL CHECK (unit_price_minor >= 0),
  PRIMARY KEY (order_id, position)
);

ALTER TABLE rma
  -- What refunding its units gives back. Null for an RMA requested before its Order was projected,
  -- until it is.
  ADD COLUMN refund_amount_minor BIGINT CHECK (refund_amount_minor >= 0),
  ADD COLUMN refund_currency CHAR(3),
  -- Its Send-back deadline and the Warehouse to send back to, from its approval on. The Warehouse
  -- is null until a Shipment kept without one is projected again.
  ADD COLUMN send_back_by TIMESTAMPTZ,
  ADD COLUMN warehouse_id VARCHAR(64),
  -- Its Inspection, once inspected.
  ADD COLUMN inspection_outcome VARCHAR(16),
  ADD COLUMN inspection_note VARCHAR(1000),
  -- Its resolution: the Refund's Payment transaction reference, or the replacement Shipment.
  ADD COLUMN payment_transaction_reference VARCHAR(255),
  ADD COLUMN replacement_shipment_id UUID;

-- Approved RMAs, by Send-back deadline, as Staff chase them.
CREATE INDEX rma_send_back_idx ON rma (status, send_back_by, id);

-- The decision history becomes the RMA history: every step, who took it and when. Sprint 5's RMA
-- decisions stay its first entries, unchanged; RMAs requested from now on start with their request.
ALTER TABLE rma_decision RENAME TO rma_history;
ALTER TABLE rma_history RENAME COLUMN decided_by TO changed_by;
ALTER TABLE rma_history RENAME COLUMN decided_at TO changed_at;
ALTER TABLE rma_history RENAME CONSTRAINT rma_decision_pkey TO rma_history_pkey;
ALTER TABLE rma_history RENAME CONSTRAINT rma_decision_rma_id_fkey TO rma_history_rma_id_fkey;

-- Sprint 5's approved RMAs gain their Send-back deadline, worked out from their approval with the
-- default period of 14 days: the later of their window's end and 14 days after it. It may have
-- passed already, so the returns Saga expires them at once. Their Warehouse is filled in once
-- their Shipment is projected again, and their Refund amount once their Order is.
UPDATE rma SET send_back_by = GREATEST(
    rma.window_ends_at, approval.changed_at + INTERVAL '14 days')
FROM rma_history approval
WHERE approval.rma_id = rma.id AND approval.status = 'APPROVED' AND rma.status = 'APPROVED';
