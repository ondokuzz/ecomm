-- What a Refund is for, such as an RMA reference; null for every other kind of Payment transaction.
ALTER TABLE payment_transaction ADD COLUMN refund_for VARCHAR(64);

-- PARTIALLY_REFUNDED is 18 characters.
ALTER TABLE payment ALTER COLUMN status TYPE VARCHAR(32);
