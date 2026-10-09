-- Each Gateway webhook Payment received, once per gateway event ID, whether it settled its Payment or
-- arrived after the Payment was already settled or voided. A repeat of an event ID changes nothing.
CREATE TABLE gateway_webhook (
  event_id          VARCHAR(255) PRIMARY KEY,
  payment_id        UUID         NOT NULL REFERENCES payment (id),
  gateway_reference VARCHAR(128) NOT NULL,
  outcome           VARCHAR(16)  NOT NULL,
  decline_reason    VARCHAR(64),
  received_at       TIMESTAMPTZ  NOT NULL
);

-- A webhook names the Payment by its authorization's gateway reference.
CREATE INDEX payment_transaction_by_authorization_reference_idx
  ON payment_transaction (gateway_reference) WHERE position = 0;
