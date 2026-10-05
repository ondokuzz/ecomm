-- Staff list every Customer's Orders newest first, a page at a time, so the list reads this index
-- rather than sorting the whole table.
CREATE INDEX customer_order_by_placed_at ON customer_order (placed_at DESC, id);
