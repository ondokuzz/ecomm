-- One row per Variant: how many units are available to sell.
CREATE TABLE stock (
  variant_id VARCHAR(64) PRIMARY KEY,
  quantity   INTEGER     NOT NULL CHECK (quantity >= 0)
);
