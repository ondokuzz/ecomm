-- A Customer's Payment transaction history: their Payments, then each one's transactions by time,
-- so it stays one query however many Orders they have.
CREATE INDEX payment_by_customer_idx ON payment (customer_id);
CREATE INDEX payment_transaction_by_payment_and_time_idx ON payment_transaction (payment_id, at);
