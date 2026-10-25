-- A Coupon's optional Usage limits: usage_limit in all, per_customer_limit by each Customer. Every
-- Coupon before them has neither, and stays unlimited.
ALTER TABLE coupon
  ADD COLUMN usage_limit        INTEGER CHECK (usage_limit >= 1),
  ADD COLUMN per_customer_limit INTEGER CHECK (per_customer_limit >= 1);

-- One row per Checkout Session holding a Coupon's redemption, until expires_at, the session's own
-- expiry; a row past it is a lapsed hold and counts for nothing. A session holds one Coupon.
CREATE TABLE redemption_hold (
  session_id  TEXT        PRIMARY KEY,
  coupon_code VARCHAR(64) NOT NULL REFERENCES coupon (code) ON DELETE CASCADE,
  customer_id TEXT        NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX redemption_hold_coupon ON redemption_hold (coupon_code, customer_id);

-- One row per paid Checkout Session that used a Coupon. It is never given back, not even when the
-- Coupon is deleted, so a code made again still counts what was redeemed under it.
CREATE TABLE coupon_redemption (
  session_id  TEXT        PRIMARY KEY,
  coupon_code VARCHAR(64) NOT NULL,
  customer_id TEXT        NOT NULL,
  redeemed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX coupon_redemption_coupon ON coupon_redemption (coupon_code, customer_id);
