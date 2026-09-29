-- Stock for the extra Variants of Catalog's multi-Variant seed Products
-- (services/catalog/src/main/resources/seed/products.json). Each Product's first Variant keeps its
-- SKU as its ID, so its Stock is in V2. One Variant starts sold out, so the Storefront has one to
-- mark.
INSERT INTO stock (variant_id, quantity) VALUES
  ('PHN-PIXEL-9-OBSIDIAN-256',      10),
  ('PHN-PIXEL-9-PORCELAIN-128',     15),
  ('PHN-IPHONE-16-BLACK-256',       20),
  ('PHN-IPHONE-16-ULTRAMARINE-128', 12),
  ('PHN-IPHONE-16-ULTRAMARINE-256',  0),
  ('LPT-MBA-13-M3-16-512',           8),
  ('LPT-MBA-13-M3-8-256',            6);
