-- Test-only: an Order as a Sprint 1 stack holds it, inserted after V1 with only the columns V1
-- created, before Orders recorded their Discount and tax (see OrderDiscountAndTaxApiTest).
INSERT INTO customer_order (id, customer_id, status, placed_at)
VALUES ('51000000-0000-4000-8000-000000000001', 'customer-42', 'PAID', '2026-09-01T10:00:00Z');

INSERT INTO order_line (order_id, position, variant_id, quantity, unit_price_minor, currency)
VALUES ('51000000-0000-4000-8000-000000000001', 0, 'PHN-PIXEL-9', 1, 79900, 'EUR');
