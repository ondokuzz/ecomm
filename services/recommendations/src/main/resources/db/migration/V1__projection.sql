-- The projection of Order Management's and Catalog's events (README). Every table can be emptied
-- and refilled from the topics: `make recommendations-rebuild`.

-- An Order per row, at the newest version applied; counts says whether it is Paid or later and not
-- Cancelled or Returned.
CREATE TABLE orders (
  order_id  TEXT                     PRIMARY KEY,
  version   BIGINT                   NOT NULL,
  counts    BOOLEAN                  NOT NULL,
  placed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- The Variants each Order bought.
CREATE TABLE order_variants (
  order_id   TEXT NOT NULL REFERENCES orders ON DELETE CASCADE,
  variant_id TEXT NOT NULL,
  PRIMARY KEY (order_id, variant_id)
);

CREATE INDEX order_variants_by_variant ON order_variants (variant_id);

-- A Product per row, at the newest version applied, and whether it has left the Catalog.
CREATE TABLE products (
  sku     TEXT    PRIMARY KEY,
  version BIGINT  NOT NULL,
  removed BOOLEAN NOT NULL
);

-- Every Variant a Product has had, so an Order for a Variant since dropped still names its Product.
CREATE TABLE variants (
  variant_id TEXT PRIMARY KEY,
  sku        TEXT NOT NULL REFERENCES products
);

CREATE INDEX variants_by_sku ON variants (sku);
