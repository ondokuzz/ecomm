-- Staff can stop stocking a Variant once nothing holds it. Its committed, released and expired
-- Reservations stay as a record of what was held, naming a Variant Inventory may no longer stock.
ALTER TABLE reservation_item DROP CONSTRAINT reservation_item_variant_id_fkey;
