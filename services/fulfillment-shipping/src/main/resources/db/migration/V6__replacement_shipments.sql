-- A Shipment has a kind: an Order's (ORDER), or a replacement for an RMA's units (REPLACEMENT),
-- which names its RMA. Every Shipment before this one is an Order's. An Order still has one ORDER
-- Shipment, and an RMA at most one replacement, which also names the RMA's Order.
ALTER TABLE shipment
  ADD COLUMN kind   VARCHAR(16) NOT NULL DEFAULT 'ORDER',
  ADD COLUMN rma_id VARCHAR(64) UNIQUE,
  ADD CONSTRAINT shipment_replacement_names_its_rma
    CHECK ((kind = 'REPLACEMENT') = (rma_id IS NOT NULL));

ALTER TABLE shipment DROP CONSTRAINT shipment_order_id_key;
CREATE UNIQUE INDEX shipment_order_idx ON shipment (order_id) WHERE kind = 'ORDER';
