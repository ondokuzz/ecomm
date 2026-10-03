-- Test-only: a Greeting the tests change and publish, and the projection a test listener keeps of
-- Greeting events.
CREATE TABLE greeting (
  id   VARCHAR(64) PRIMARY KEY,
  text TEXT        NOT NULL
);

CREATE TABLE greeting_projection (
  greeting_id VARCHAR(64) PRIMARY KEY,
  version     BIGINT      NOT NULL,
  text        TEXT        NOT NULL
);
