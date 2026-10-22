-- Each Customer's Notification preferences, once they save them; one who never did gets email only.
CREATE TABLE notification_preferences (
  customer_id  TEXT                     PRIMARY KEY,
  -- E.164, such as +4915112345678.
  phone_number TEXT,
  sms          BOOLEAN                  NOT NULL,
  push         BOOLEAN                  NOT NULL,
  updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

-- What the mock SMS and push providers would have sent (ADR 0005), each in its own table, for Staff
-- to read. A real SMS gateway or push service replaces them.
CREATE TABLE mock_sms_message (
  reference    TEXT                     PRIMARY KEY,
  customer_id  TEXT                     NOT NULL,
  phone_number TEXT                     NOT NULL,
  text         TEXT                     NOT NULL,
  sent_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX mock_sms_message_by_customer ON mock_sms_message (customer_id, sent_at);

CREATE TABLE mock_push_message (
  reference   TEXT                     PRIMARY KEY,
  customer_id TEXT                     NOT NULL,
  title       TEXT                     NOT NULL,
  text        TEXT                     NOT NULL,
  sent_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX mock_push_message_by_customer ON mock_push_message (customer_id, sent_at);
