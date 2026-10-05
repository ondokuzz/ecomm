-- Test-only: Stock and Reservations as a Sprint 2 stack holds them, inserted before the ledger and
-- Stock events exist, so the migration opens their balances and the startup job publishes them
-- (see BackfillApiTest).
INSERT INTO stock (variant_id, on_hand) VALUES
  ('SPRINT2-HELD', 9),
  ('SPRINT2-MOVING', 4),
  ('SPRINT2-EXPIRED', 5);

INSERT INTO reservation (id, customer_id, status, expires_at) VALUES
  -- Still holding Stock.
  ('52000000-0000-4000-8000-000000000001', 'customer-sprint2', 'ACTIVE', '2099-01-01T00:00:00Z'),
  -- Expired, but not yet swept.
  ('52000000-0000-4000-8000-000000000002', 'customer-sprint2', 'ACTIVE', '2026-09-20T09:00:00Z'),
  -- Done with: its units already left On-hand, so it gets no movement.
  ('52000000-0000-4000-8000-000000000003', 'customer-sprint2', 'COMMITTED', '2026-09-20T09:00:00Z');

INSERT INTO reservation_item (reservation_id, variant_id, quantity) VALUES
  ('52000000-0000-4000-8000-000000000001', 'SPRINT2-HELD', 2),
  ('52000000-0000-4000-8000-000000000001', 'SPRINT2-MOVING', 1),
  ('52000000-0000-4000-8000-000000000002', 'SPRINT2-EXPIRED', 3),
  ('52000000-0000-4000-8000-000000000003', 'SPRINT2-HELD', 3);
