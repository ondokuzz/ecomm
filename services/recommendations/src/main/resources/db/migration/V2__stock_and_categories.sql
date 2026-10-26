-- Sprint 6: Recommendations leaves out Products with nothing in Stock and fills up with a
-- Category's best sellers (README).

-- A Product's Category's slug. Products kept before Sprint 6 have none until catalog.product is
-- read again from the start, which the service does on start while any is without one.
ALTER TABLE products ADD COLUMN category TEXT;

CREATE INDEX products_by_category ON products (category);

-- Whether the Variant is one the Product has now, rather than one it has since dropped. Only its
-- current Variants' Stock says whether a Product is in Stock.
ALTER TABLE variants ADD COLUMN current BOOLEAN NOT NULL DEFAULT true;

-- A Category per row, at the newest version applied: its name, and whether it was removed.
CREATE TABLE categories (
  slug    TEXT    PRIMARY KEY,
  version BIGINT  NOT NULL,
  name    TEXT    NOT NULL,
  removed BOOLEAN NOT NULL
);

-- A Variant's Stock per row, at the newest version applied: whether Inventory stocks it and has
-- some available. A Variant with no row is out of Stock.
CREATE TABLE stock (
  variant_id TEXT    PRIMARY KEY,
  version    BIGINT  NOT NULL,
  in_stock   BOOLEAN NOT NULL
);

-- Orders within the lookback.
CREATE INDEX orders_by_placed_at ON orders (placed_at);
