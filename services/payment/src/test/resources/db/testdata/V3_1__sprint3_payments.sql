-- Test-only: Payments as a Sprint 3 stack holds them, inserted after V3 and before the ledger and
-- Payment events exist, so the migration and the startup job backfill them.
INSERT INTO payment
  (id, customer_id, order_id, amount_minor, currency, status, decline_reason, gateway_reference)
VALUES
  ('5e000000-0000-4000-8000-0000000000a1', 'customer-sprint3', 'order-sprint3-paid', 79900, 'EUR',
   'AUTHORIZED', NULL, 'mock-sprint3-approved'),
  ('5e000000-0000-4000-8000-0000000000a2', 'customer-sprint3', 'order-sprint3-cancelled', 14950,
   'EUR', 'DECLINED', 'card_declined', 'mock-sprint3-declined'),
  ('5e000000-0000-4000-8000-0000000000a3', 'customer-sprint3', 'order-sprint3-cancelled', 14950,
   'EUR', 'AUTHORIZED', NULL, 'mock-sprint3-retried');
