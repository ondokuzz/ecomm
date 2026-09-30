-- One row per Coupon, keyed by its upper-case code. A discount is PERCENT_OFF, with percent_off,
-- or AMOUNT_OFF, with amount_off_minor and amount_off_currency; the minimum subtotal is optional.
CREATE TABLE coupon (
  code                      VARCHAR(64) PRIMARY KEY CHECK (code = UPPER(code)),
  discount_type             VARCHAR(16) NOT NULL,
  percent_off               INTEGER     CHECK (percent_off BETWEEN 1 AND 100),
  amount_off_minor          BIGINT      CHECK (amount_off_minor > 0),
  amount_off_currency       CHAR(3),
  minimum_subtotal_minor    BIGINT      CHECK (minimum_subtotal_minor >= 0),
  minimum_subtotal_currency CHAR(3),
  valid_from                TIMESTAMPTZ NOT NULL,
  valid_until               TIMESTAMPTZ NOT NULL CHECK (valid_until > valid_from),
  active                    BOOLEAN     NOT NULL
);
