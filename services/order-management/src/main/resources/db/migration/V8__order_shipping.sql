-- An Order's Shipping: the Shipping address Checkout validated, and the Shipping option chosen, with
-- its Carrier and its Shipping cost in the currency of the Order's lines. An Order placed before
-- this migration has none, and every column is null; a new one has every required column.
ALTER TABLE customer_order
  ADD COLUMN shipping_recipient_name VARCHAR(100),
  ADD COLUMN shipping_line1          VARCHAR(100),
  ADD COLUMN shipping_line2          VARCHAR(100),
  ADD COLUMN shipping_postcode       VARCHAR(16),
  ADD COLUMN shipping_city           VARCHAR(100),
  ADD COLUMN shipping_region         VARCHAR(100),
  ADD COLUMN shipping_country        VARCHAR(2),
  ADD COLUMN shipping_phone          VARCHAR(32),
  ADD COLUMN shipping_option_id      VARCHAR(32),
  ADD COLUMN shipping_carrier        VARCHAR(64),
  ADD COLUMN shipping_cost_minor     BIGINT CHECK (shipping_cost_minor >= 0),
  ADD CONSTRAINT shipping_is_whole CHECK (
    (shipping_recipient_name IS NULL AND shipping_line1 IS NULL AND shipping_line2 IS NULL
       AND shipping_postcode IS NULL AND shipping_city IS NULL AND shipping_region IS NULL
       AND shipping_country IS NULL AND shipping_phone IS NULL AND shipping_option_id IS NULL
       AND shipping_carrier IS NULL AND shipping_cost_minor IS NULL)
    OR (shipping_recipient_name IS NOT NULL AND shipping_line1 IS NOT NULL
       AND shipping_postcode IS NOT NULL AND shipping_city IS NOT NULL
       AND shipping_country IS NOT NULL AND shipping_option_id IS NOT NULL
       AND shipping_carrier IS NOT NULL AND shipping_cost_minor IS NOT NULL));
