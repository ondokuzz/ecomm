-- A Payment's Payment transactions, its ledger (ADR 0002): one row per interaction with the gateway,
-- oldest first. payment.status is the status worked out from them, written in the same transaction
-- as each one, so a read stays one query.
CREATE TABLE payment_transaction (
  payment_id        UUID         NOT NULL REFERENCES payment (id),
  position          INTEGER      NOT NULL CHECK (position >= 0),
  kind              VARCHAR(16)  NOT NULL,
  amount_minor      BIGINT       NOT NULL CHECK (amount_minor > 0),
  currency          CHAR(3)      NOT NULL,
  outcome           VARCHAR(16)  NOT NULL,
  gateway_reference VARCHAR(128) NOT NULL,
  decline_reason    VARCHAR(64),
  -- The gateway's event ID, when a webhook recorded the transaction.
  gateway_event_id  VARCHAR(255),
  at                TIMESTAMPTZ  NOT NULL,
  -- Reconstructed by this migration from a Payment recorded before the ledger; at is when.
  backfilled        BOOLEAN      NOT NULL,
  PRIMARY KEY (payment_id, position)
);

-- Transactions are only ever inserted.
CREATE FUNCTION payment_transaction_is_append_only() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'Payment transactions are never changed or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_transaction_append_only
  BEFORE UPDATE OR DELETE ON payment_transaction
  FOR EACH ROW EXECUTE FUNCTION payment_transaction_is_append_only();

-- Sprints 1 to 3 kept one row per Payment, with no time. Each becomes a Payment with one backfilled
-- authorization: approved for an AUTHORIZED one, declined with its reason for a DECLINED one.
INSERT INTO payment_transaction
  (payment_id, position, kind, amount_minor, currency, outcome, gateway_reference, decline_reason,
   at, backfilled)
SELECT id, 0, 'AUTHORIZATION', amount_minor, currency,
       CASE status WHEN 'AUTHORIZED' THEN 'APPROVED' ELSE 'DECLINED' END,
       gateway_reference, decline_reason, now(), TRUE
FROM payment;

ALTER TABLE payment DROP COLUMN gateway_reference, DROP COLUMN decline_reason;

-- A Customer's Payments for one of their Orders, and every Payment for an Order for Staff.
CREATE INDEX payment_by_order_idx ON payment (order_id);

-- The Payments still to be published once with change BACKFILLED, so that consumers start
-- complete: every Payment that existed before Payment events. The startup job deletes each row as it
-- publishes it.
CREATE TABLE payment_awaiting_backfill_event (
  payment_id UUID PRIMARY KEY REFERENCES payment (id)
);

INSERT INTO payment_awaiting_backfill_event (payment_id) SELECT id FROM payment;
