-- One row per Payment: an Order's amount and what the gateway answered.
CREATE TABLE payment (
  id                UUID         PRIMARY KEY,
  order_id          VARCHAR(64)  NOT NULL,
  amount_minor      BIGINT       NOT NULL CHECK (amount_minor > 0),
  currency          CHAR(3)      NOT NULL,
  status            VARCHAR(16)  NOT NULL,
  gateway_reference VARCHAR(128) NOT NULL
);
