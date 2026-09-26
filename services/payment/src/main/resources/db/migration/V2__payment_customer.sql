-- A Payment belongs to the Customer who authorized it: the access token's sub.
ALTER TABLE payment ADD COLUMN customer_id VARCHAR(255) NOT NULL;
