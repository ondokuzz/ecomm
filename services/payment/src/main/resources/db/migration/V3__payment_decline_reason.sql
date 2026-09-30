-- A declined Payment keeps the gateway's reason, such as insufficient_funds; null otherwise.
ALTER TABLE payment ADD COLUMN decline_reason VARCHAR(64);
