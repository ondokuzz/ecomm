-- RMAs are found by their RMA reference, the first eight characters of the ID, which isn't stored;
-- this index on that prefix serves the lookup, a Customer's and Staff's alike.
CREATE INDEX rma_by_reference_idx ON rma (left(id::text, 8), customer_id);
