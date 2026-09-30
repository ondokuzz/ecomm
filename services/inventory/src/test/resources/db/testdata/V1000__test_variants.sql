-- Test-only Variants, so tests that change stock never touch the seed or each other.
INSERT INTO stock (variant_id, on_hand) VALUES
  ('TEST-AUTH', 8);
