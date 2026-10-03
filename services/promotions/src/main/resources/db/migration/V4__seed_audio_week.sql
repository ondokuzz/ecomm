-- The demo Campaign, so a demo checkout in the audio Category gets a Discount without a code: 15%
-- off audio lines, no minimum, valid for a year from when the database was created. `make
-- seed-reset` recreates the database, and with it a fresh year.
INSERT INTO campaign (id, name, discount_type, percent_off, categories, valid_from, valid_until,
                      active, priority)
VALUES (gen_random_uuid(), 'Audio week', 'PERCENT_OFF', 15, '{audio}', now(),
        now() + INTERVAL '1 year', true, 10);
