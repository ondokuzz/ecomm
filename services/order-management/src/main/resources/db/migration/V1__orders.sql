-- One row per Order ("order" is reserved, hence customer_order); its total is summed from its lines.
CREATE TABLE customer_order (
  id          UUID         PRIMARY KEY,
  customer_id VARCHAR(255) NOT NULL,
  status      VARCHAR(16)  NOT NULL,
  placed_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX customer_order_by_customer ON customer_order (customer_id, placed_at DESC);

-- An Order's lines, in the order they were placed: one Variant each, priced at checkout.
CREATE TABLE order_line (
  order_id         UUID        NOT NULL REFERENCES customer_order (id),
  position         INTEGER     NOT NULL,
  variant_id       VARCHAR(64) NOT NULL,
  quantity         INTEGER     NOT NULL CHECK (quantity > 0),
  unit_price_minor BIGINT      NOT NULL CHECK (unit_price_minor >= 0),
  currency         CHAR(3)     NOT NULL,
  PRIMARY KEY (order_id, position)
);
