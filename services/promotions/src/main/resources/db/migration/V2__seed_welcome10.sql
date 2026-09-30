-- The demo Coupon, so the demo checkout works on a fresh stack: 10% off, no minimum, valid for a
-- year from when the database was created. `make seed-reset` recreates the database, and with it
-- a fresh year.
INSERT INTO coupon (code, discount_type, percent_off, valid_from, valid_until, active)
VALUES ('WELCOME10', 'PERCENT_OFF', 10, now(), now() + INTERVAL '1 year', true);
