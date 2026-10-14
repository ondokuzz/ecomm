-- A Shipment: one Order's parcel, from the Warehouse assigned to its Shipping address. One Order has
-- one Shipment. version goes up by one with every change, under the row's lock.
CREATE TABLE shipment (
  id                 UUID         PRIMARY KEY,
  order_id           VARCHAR(64)  NOT NULL UNIQUE,
  customer_id        VARCHAR(255) NOT NULL,
  warehouse_id       VARCHAR(32)  NOT NULL,
  status             VARCHAR(16)  NOT NULL,
  version            BIGINT       NOT NULL CHECK (version >= 1),
  created_at         TIMESTAMPTZ  NOT NULL,
  -- The Shipping address.
  recipient_name     VARCHAR(255) NOT NULL,
  line1              VARCHAR(255) NOT NULL,
  line2              VARCHAR(255),
  postcode           VARCHAR(32)  NOT NULL,
  city               VARCHAR(255) NOT NULL,
  region             VARCHAR(255),
  country            CHAR(2)      NOT NULL,
  phone              VARCHAR(64),
  -- The Shipping option the Order was placed with, and its Shipping cost.
  option_id          VARCHAR(64)  NOT NULL,
  option_carrier     VARCHAR(255) NOT NULL,
  cost_minor         BIGINT       NOT NULL,
  currency           CHAR(3)      NOT NULL,
  estimated_days     INTEGER,
  -- Once booked with the Carrier.
  carrier            VARCHAR(255),
  tracking_number    VARCHAR(64)  UNIQUE,
  delivered_at       TIMESTAMPTZ,
  hold_reason        VARCHAR(255)
);

-- Staff's fulfillment queue: by status and Warehouse, oldest first.
CREATE INDEX shipment_queue_idx ON shipment (status, warehouse_id, created_at);

CREATE TABLE shipment_line (
  shipment_id     UUID         NOT NULL REFERENCES shipment (id),
  position        INTEGER      NOT NULL CHECK (position >= 0),
  variant_id      VARCHAR(64)  NOT NULL,
  product_name    VARCHAR(255) NOT NULL,
  quantity        INTEGER      NOT NULL CHECK (quantity >= 1),
  serial_tracking VARCHAR(8)   NOT NULL,
  PRIMARY KEY (shipment_id, position)
);

-- The Serial number of each unit packed, one row per unit. A Serial number ships on at most one
-- Shipment; Sprint 6's returns revisit this for a returned unit that ships again.
CREATE TABLE shipment_serial_number (
  serial_number VARCHAR(40) PRIMARY KEY,
  shipment_id   UUID        NOT NULL REFERENCES shipment (id),
  line_position INTEGER     NOT NULL,
  unit          INTEGER     NOT NULL CHECK (unit >= 0)
);

CREATE INDEX shipment_serial_number_by_shipment_idx ON shipment_serial_number (shipment_id);

-- Every status a Shipment has been in, and who moved it there: a Staff member's ID, ORCHESTRATION or
-- CARRIER. Rows are only ever inserted, in the transaction of the change.
CREATE TABLE shipment_status_history (
  shipment_id UUID         NOT NULL REFERENCES shipment (id),
  position    INTEGER      NOT NULL CHECK (position >= 0),
  status      VARCHAR(16)  NOT NULL,
  at          TIMESTAMPTZ  NOT NULL,
  changed_by  VARCHAR(255) NOT NULL,
  PRIMARY KEY (shipment_id, position)
);

-- Each Tracking event a Carrier webhook reported, once per Carrier event ID, whether or not it moved
-- the Shipment on. A repeat of an event ID changes nothing.
CREATE TABLE tracking_event (
  event_id    VARCHAR(255) PRIMARY KEY,
  shipment_id UUID         NOT NULL REFERENCES shipment (id),
  status      VARCHAR(16)  NOT NULL,
  occurred_at TIMESTAMPTZ  NOT NULL,
  received_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX tracking_event_by_shipment_idx ON tracking_event (shipment_id, occurred_at);
