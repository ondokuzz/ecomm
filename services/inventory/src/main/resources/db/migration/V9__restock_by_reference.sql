-- A Variant is restocked once per reference, an RMA's: a restock naming one it has is answered with
-- the Stock as it is. Looked up under the stock row's lock, so not unique here: restocks recorded
-- before the rule, from the .http file say, may share a reference.
CREATE INDEX stock_movement_restock_reference_idx ON stock_movement (variant_id, reference)
  WHERE kind = 'RESTOCKED';
