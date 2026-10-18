-- A Serial number is unique for its Product, not across every Product: two manufacturers may number
-- their units alike. And a Shipment put on hold gives its units' numbers back (released), so a
-- unit unpacked from it can be packed on another Shipment; the held Shipment still shows them.
-- Each row is now one unit of a Shipment's line.
ALTER TABLE shipment_serial_number
  ADD COLUMN product_sku VARCHAR(128),
  ADD COLUMN released    BOOLEAN NOT NULL DEFAULT false;

UPDATE shipment_serial_number n
SET product_sku = COALESCE(v.sku, l.variant_id)
FROM shipment_line l
LEFT JOIN product_variant v ON v.variant_id = l.variant_id
WHERE l.shipment_id = n.shipment_id AND l.position = n.line_position;

UPDATE shipment_serial_number n
SET released = true
FROM shipment s
WHERE s.id = n.shipment_id AND s.status = 'ON_HOLD';

ALTER TABLE shipment_serial_number ALTER COLUMN product_sku SET NOT NULL;

ALTER TABLE shipment_serial_number DROP CONSTRAINT shipment_serial_number_pkey;
ALTER TABLE shipment_serial_number ADD PRIMARY KEY (shipment_id, line_position, unit);
DROP INDEX shipment_serial_number_by_shipment_idx;

-- A unit's Serial number is on one Shipment at a time for its Product, until that one is held.
CREATE UNIQUE INDEX shipment_serial_number_unit_idx
  ON shipment_serial_number (product_sku, serial_number) WHERE NOT released;
