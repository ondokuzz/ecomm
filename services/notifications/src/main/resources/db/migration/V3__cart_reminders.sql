-- A Customer's consent to cart reminders, which are marketing: off for every Customer until they
-- turn it on, those who saved their preferences before it existed included.
ALTER TABLE notification_preferences ADD COLUMN cart_reminders BOOLEAN NOT NULL DEFAULT false;

-- Each Variant's Price as Catalog last published it, and when it last changed, from the event's
-- occurredAt: a Cart reminder says when a line's Price changed after its Cart was last changed.
-- Null until a Product's next event, or `make notifications-rebuild`, gives it one.
ALTER TABLE product_variant
  ADD COLUMN price_minor      BIGINT,
  ADD COLUMN price_currency   TEXT,
  ADD COLUMN price_changed_at TIMESTAMP WITH TIME ZONE;
