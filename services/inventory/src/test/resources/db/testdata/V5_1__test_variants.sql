-- Test-only Variants, so tests that change stock never touch the seed or each other. Inserted
-- before the ledger exists, so like the seed they get an opening balance (see BackfillApiTest).
INSERT INTO stock (variant_id, on_hand) VALUES
  ('TEST-AUTH', 8);
