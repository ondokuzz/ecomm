-- One row per Campaign, keyed by its ID. Its discount is as a Coupon's: PERCENT_OFF, with
-- percent_off, or AMOUNT_OFF, with amount_off_minor and amount_off_currency. categories holds the
-- slugs of the Catalog Categories it is limited to, in the order Staff gave them; empty means
-- every line. No two Campaigns share a priority, which orders how they apply.
CREATE TABLE campaign (
  id                        UUID         PRIMARY KEY,
  name                      VARCHAR(100) NOT NULL,
  discount_type             VARCHAR(16)  NOT NULL,
  percent_off               INTEGER      CHECK (percent_off BETWEEN 1 AND 100),
  amount_off_minor          BIGINT       CHECK (amount_off_minor > 0),
  amount_off_currency       CHAR(3),
  categories                TEXT[]       NOT NULL DEFAULT '{}',
  minimum_subtotal_minor    BIGINT       CHECK (minimum_subtotal_minor >= 0),
  minimum_subtotal_currency CHAR(3),
  valid_from                TIMESTAMPTZ  NOT NULL,
  valid_until               TIMESTAMPTZ  NOT NULL CHECK (valid_until > valid_from),
  active                    BOOLEAN      NOT NULL,
  priority                  INTEGER      NOT NULL UNIQUE CHECK (priority >= 0)
);
