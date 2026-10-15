-- Fulfillment & Shipping's Shipments, projected from fulfillment-shipping.shipment (group
-- returns-warranty.shipments) for what an RMA is checked against: the Order, its Customer, what was
-- delivered and when. Every Shipment is kept at its newest version; only a DELIVERED one can be
-- claimed against.
CREATE TABLE shipment (
  id           UUID         PRIMARY KEY,
  version      BIGINT       NOT NULL,
  order_id     VARCHAR(64)  NOT NULL UNIQUE,
  customer_id  VARCHAR(255) NOT NULL,
  status       VARCHAR(32)  NOT NULL,
  delivered_at TIMESTAMPTZ
);

CREATE TABLE shipment_line (
  shipment_id     UUID         NOT NULL REFERENCES shipment (id) ON DELETE CASCADE,
  variant_id      VARCHAR(64)  NOT NULL,
  product_name    VARCHAR(255) NOT NULL,
  quantity        INTEGER      NOT NULL,
  serial_tracking VARCHAR(8)   NOT NULL,
  -- The Serial number of each unit, in the order Staff packed them.
  serial_numbers  TEXT[]       NOT NULL,
  PRIMARY KEY (shipment_id, variant_id)
);

-- Catalog's Products, projected from catalog.product (group returns-warranty.products) for each
-- Variant's Warranty terms and Serial tracking. A Product keeps every Variant it has ever had.
CREATE TABLE product (
  sku                   VARCHAR(128) PRIMARY KEY,
  version               BIGINT       NOT NULL,
  serial_tracking       VARCHAR(8)   NOT NULL,
  -- Both null when the Product has no Warranty terms.
  warranty_months       INTEGER,
  warranty_manufacturer VARCHAR(255)
);

CREATE TABLE product_variant (
  variant_id VARCHAR(64)  PRIMARY KEY,
  sku        VARCHAR(128) NOT NULL REFERENCES product (sku) ON DELETE CASCADE
);
