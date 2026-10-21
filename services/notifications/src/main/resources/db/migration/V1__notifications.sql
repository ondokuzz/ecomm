-- The Notification ledger (README): a row per Notification, one message on one channel to one
-- Customer, and a row per attempt at sending it.
CREATE TABLE notification (
  id                 UUID                     PRIMARY KEY,
  -- The triggering event's eventId; with the Message type and channel, it is recorded once.
  event_id           TEXT                     NOT NULL,
  message_type       TEXT                     NOT NULL,
  channel            TEXT                     NOT NULL,
  customer_id        TEXT                     NOT NULL,
  order_id           TEXT,
  rma_id             TEXT,
  -- What the message is rendered from, taken from the event: JSON of its Message type's content.
  content            JSONB                    NOT NULL,
  status             TEXT                     NOT NULL,
  -- The address, the message as last rendered and the provider's reference, once known.
  recipient          TEXT,
  subject            TEXT,
  html_body          TEXT,
  text_body          TEXT,
  provider_reference TEXT,
  created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
  next_attempt_at    TIMESTAMP WITH TIME ZONE,
  sent_at            TIMESTAMP WITH TIME ZONE,
  UNIQUE (event_id, message_type, channel)
);

CREATE INDEX notification_due ON notification (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX notification_by_order ON notification (order_id, created_at);
CREATE INDEX notification_by_rma ON notification (rma_id, created_at);
CREATE INDEX notification_by_customer ON notification (customer_id, created_at);

CREATE TABLE notification_attempt (
  notification_id UUID                     NOT NULL REFERENCES notification ON DELETE CASCADE,
  attempt         INT                      NOT NULL,
  at              TIMESTAMP WITH TIME ZONE NOT NULL,
  outcome         TEXT                     NOT NULL,
  reason          TEXT,
  PRIMARY KEY (notification_id, attempt)
);

-- The projection of Catalog's Products that names Order Lines. Both tables can be emptied and
-- refilled from catalog.product: `make notifications-rebuild`.
CREATE TABLE product (
  sku     TEXT   PRIMARY KEY,
  version BIGINT NOT NULL,
  name    TEXT   NOT NULL
);

-- Every Variant a Product has had, with its axis values as one label, such as "128 GB, Obsidian".
CREATE TABLE product_variant (
  variant_id TEXT PRIMARY KEY,
  sku        TEXT NOT NULL REFERENCES product,
  label      TEXT NOT NULL
);
