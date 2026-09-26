-- Stock for Catalog's seed Products (services/catalog/src/main/resources/seed/products.json).
-- Each has one Variant, whose ID is its SKU. As a migration it runs once, so a restart never
-- undoes a decrement.
INSERT INTO stock (variant_id, quantity) VALUES
  ('PHN-PIXEL-9',         25),
  ('PHN-PIXEL-9-PRO',     12),
  ('PHN-IPHONE-16',       40),
  ('PHN-IPHONE-16-PRO',   15),
  ('PHN-GALAXY-S24',      20),
  ('PHN-GALAXY-A55',      35),
  ('PHN-NOTHING-2A',       8),
  ('LPT-MBA-13-M3',       18),
  ('LPT-MBP-14-M4',       10),
  ('LPT-XPS-13',          12),
  ('LPT-THINKPAD-X1',      7),
  ('LPT-ZENBOOK-14',      14),
  ('LPT-FRAMEWORK-13',     5),
  ('LPT-SURFACE-7',        9),
  ('AUD-AIRPODS-PRO-2',   50),
  ('AUD-SONY-WH1000XM5',  22),
  ('AUD-BOSE-QC-ULTRA',   16),
  ('AUD-SENNHEISER-MTW4', 11),
  ('AUD-JBL-FLIP-6',      30),
  ('AUD-SONOS-ERA-100',    3);
