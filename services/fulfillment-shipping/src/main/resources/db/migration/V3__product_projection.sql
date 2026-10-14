-- Catalog's Products, projected from catalog.product (group fulfillment-shipping.products) for what
-- a Shipment's lines need: each Variant's Product name and Serial tracking. A Product keeps every
-- Variant it has ever had, so an Order for a Variant Catalog has since dropped still ships.
CREATE TABLE product (
  sku             VARCHAR(128) PRIMARY KEY,
  version         BIGINT       NOT NULL,
  name            VARCHAR(255) NOT NULL,
  serial_tracking VARCHAR(8)   NOT NULL
);

CREATE TABLE product_variant (
  variant_id VARCHAR(64) PRIMARY KEY,
  sku        VARCHAR(128) NOT NULL REFERENCES product (sku)
);
