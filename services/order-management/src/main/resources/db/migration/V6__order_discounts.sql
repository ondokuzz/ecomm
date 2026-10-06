-- An Order's Discounts, in the order they applied: a Campaign's, named by its ID and name, or a
-- Coupon's, named by its code. Until now an Order held at most one, a Coupon's, in customer_order;
-- each moves here as a COUPON Discount, and those columns go.
CREATE TABLE order_discount (
  order_id      UUID         NOT NULL REFERENCES customer_order (id),
  position      INTEGER      NOT NULL CHECK (position >= 0),
  source        VARCHAR(16)  NOT NULL CHECK (source IN ('CAMPAIGN', 'COUPON')),
  coupon_code   VARCHAR(64),
  campaign_id   VARCHAR(64),
  campaign_name VARCHAR(100),
  amount_minor  BIGINT       NOT NULL CHECK (amount_minor >= 0),
  PRIMARY KEY (order_id, position),
  CONSTRAINT discount_names_its_source CHECK (
    (source = 'COUPON' AND coupon_code IS NOT NULL AND campaign_id IS NULL
       AND campaign_name IS NULL)
    OR (source = 'CAMPAIGN' AND coupon_code IS NULL AND campaign_id IS NOT NULL
       AND campaign_name IS NOT NULL))
);

INSERT INTO order_discount (order_id, position, source, coupon_code, amount_minor)
SELECT id, 0, 'COUPON', coupon_code, discount_minor FROM customer_order WHERE coupon_code IS NOT NULL;

ALTER TABLE customer_order
  DROP CONSTRAINT discount_has_code_and_amount,
  DROP COLUMN coupon_code,
  DROP COLUMN discount_minor;
