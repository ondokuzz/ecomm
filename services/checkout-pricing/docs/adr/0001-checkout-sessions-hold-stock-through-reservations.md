# Checkout Sessions hold Stock through Reservations

Checkout happens in two steps. Starting it creates a Checkout Session: the Cart's lines at the Prices Catalog gives at that moment, their tax, and an Inventory Reservation of their Stock, kept for 15 minutes. Paying the session places the Order at those Prices, authorizes the Payment, and commits the Reservation, which takes the units off on-hand for good. A session that isn't paid in time just expires. Its Reservation expires 2 minutes after the session, and Inventory stops counting it the moment it does, so nothing has to run to give the Stock back. The Reservation outlives the session by those 2 minutes so that a payment started just before the session expires can still commit it. A session lives in Redis, keyed by its ID, and each Customer has a pointer to their one session. Its TTL runs to its Reservation's expiry rather than its own, so for those 2 minutes Checkout can still tell a late payment (410) apart from an unknown session (404); its own `expiresAt` is what decides it has expired. Checkout reads its clock through a `TimeSource` port.

## Considered Options

- **Take Stock when the Customer pays, as Sprint 1 did (`POST /stock/decrement`).** Rejected because the last unit can sell out between the Customer seeing the checkout page and pressing Pay, and nothing on that page promises them the units or the Price.
- **Hold Stock in Checkout itself.** Rejected because Inventory owns Stock. Two services counting holds on the same units could disagree, so the hold has to be Inventory's own concept, the Reservation, and Inventory has to be the one that decides it has lapsed.
- **Have Checkout release an expired session's Reservation.** Rejected because it needs a Checkout-side sweeper or Redis keyspace notifications, and a Checkout outage would then leak holds. Expiring the Reservation in Inventory needs no one to act.
- **Keep sessions in Postgres.** Rejected because a session is short-lived and read by key, and a TTL is its natural lifetime. Redis already runs for Cart.

## Consequences

- The Prices a Customer sees at checkout are the Prices they pay, even if Catalog changes them mid-session. A Price change only reaches new sessions.
- Starting checkout again replaces the session and releases its Reservation first, so the old hold doesn't count against the new one.
- Paying a session that has expired is a 410, and nothing happens. Once Redis has dropped it, 2 minutes later, paying it is a 404.
- Until the Sagas arrive (Sprint 3), Checkout calls Inventory, Order Management and Payment directly. If the commit fails after the Payment is authorized, the Order is cancelled but the Payment stays authorized.
- `POST /stock/decrement` is gone, and Staff setting on-hand Stock can never drop it below what active Reservations hold.
