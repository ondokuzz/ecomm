-- A Customer finds an Order by its Order reference, the first eight characters of its ID, which
-- isn't stored; this index on that prefix serves the lookup.
CREATE INDEX customer_order_by_reference ON customer_order (left(id::text, 8), customer_id);
