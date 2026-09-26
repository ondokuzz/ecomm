-- Test-only Variants, so tests that change stock never touch the seed or each other.
INSERT INTO stock (variant_id, quantity) VALUES
  ('TEST-BATCH-A', 10),
  ('TEST-BATCH-B', 5),
  ('TEST-REJECT-A', 10),
  ('TEST-REJECT-B', 1),
  ('TEST-EXACT', 3),
  ('TEST-DUPLICATE', 4),
  ('TEST-UNKNOWN-MIX', 6),
  ('TEST-INVALID', 7),
  ('TEST-AUTH', 8);
