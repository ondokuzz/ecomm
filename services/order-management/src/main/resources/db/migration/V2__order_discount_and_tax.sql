-- An Order's discount and tax, in the currency of its lines. An Order placed before this migration
-- has no discount and zero tax, which is what it was charged.
ALTER TABLE customer_order
  ADD COLUMN coupon_code    VARCHAR(64),
  ADD COLUMN discount_minor BIGINT CHECK (discount_minor >= 0),
  ADD COLUMN tax_minor      BIGINT NOT NULL DEFAULT 0 CHECK (tax_minor >= 0),
  ADD CONSTRAINT discount_has_code_and_amount
    CHECK ((coupon_code IS NULL) = (discount_minor IS NULL));
