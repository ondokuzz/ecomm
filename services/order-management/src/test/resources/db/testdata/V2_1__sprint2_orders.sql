-- Test-only: Orders as a Sprint 2 stack holds them, inserted after V2 and before Order Status
-- histories and Order events exist, so the migration and the startup job backfill them.
INSERT INTO customer_order (id, customer_id, status, placed_at, coupon_code, discount_minor, tax_minor)
VALUES
  ('5e000000-0000-4000-8000-000000000001', 'customer-sprint2', 'PLACED',
   '2026-09-20T09:00:00Z', NULL, NULL, 0),
  ('5e000000-0000-4000-8000-000000000002', 'customer-sprint2', 'CANCELLED',
   '2026-09-21T09:00:00Z', 'WELCOME10', 7990, 14382),
  ('5e000000-0000-4000-8000-000000000003', 'customer-sprint2', 'PLACED',
   '2026-09-22T09:00:00Z', NULL, NULL, 0);

INSERT INTO order_line (order_id, position, variant_id, quantity, unit_price_minor, currency)
VALUES
  ('5e000000-0000-4000-8000-000000000001', 0, 'PHN-PIXEL-9', 1, 79900, 'EUR'),
  ('5e000000-0000-4000-8000-000000000002', 0, 'PHN-PIXEL-9', 1, 79900, 'EUR'),
  ('5e000000-0000-4000-8000-000000000003', 0, 'PHN-PIXEL-9', 1, 79900, 'EUR');
