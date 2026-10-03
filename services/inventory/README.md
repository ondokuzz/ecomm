# Inventory

Stock levels per Variant. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Data lives in the `inventory` database on the shared Postgres,
with the schema under Flyway ([`db/migration`](./src/main/resources/db/migration)).

Stock is counted per Variant as **on-hand** units, some of which Reservations may hold. What is
left is available to sell. Checkout takes Stock only through Reservations: a Checkout Session
reserves the Cart's Stock, and paying it commits the Reservation
([Checkout ADR 0001](../checkout-pricing/docs/adr/0001-checkout-sessions-hold-stock-through-reservations.md)).
A ledger of Stock movements follows in Sprint 3 (see the [roadmap](../../docs/roadmap.md) and [ADR 0002](../../docs/adr/0002-ledgers-and-outboxes-not-event-sourcing.md)).

## API

| Endpoint | Who | |
|---|---|---|
| `GET /stock/{variantId}` | anyone | `{"variantId", "quantity", "onHand", "reserved"}`; 404 for an unknown Variant |
| `PUT /stock/{variantId}` | Staff | Sets on-hand Stock: 201 for a new Variant, 200 otherwise |
| `DELETE /stock/{variantId}` | Staff | Stops stocking a Variant: 204; 404 for an unknown Variant, 409 while Reservations hold some |
| `POST /reservations` | Checkout | Holds a batch for a Customer until `expiresAt`; 201 with the Reservation |
| `POST /reservations/{id}/commit` | Checkout | Takes the held Stock off on-hand for good |
| `POST /reservations/{id}/release` | Checkout | Gives the held Stock back |

`quantity` is what is available to sell: `onHand` less what `ACTIVE` Reservations that haven't
expired yet hold (`reserved`).

Setting on-hand Stock needs a Staff token (`STAFF` role). It takes `{"onHand": 12}` and adds the
Variant if Inventory doesn't stock it yet. Setting it below `reserved` is a 409 whose `reserved`
says how many units Reservations hold; a negative or non-integer count is a 400.

Staff stop stocking a Variant when its Product leaves the Catalog; the
[Admin Console](../../frontend/admin-console/README.md) does it when it deletes a Product. Its
stock is then a 404, and reserving it is a 404 with `unknownVariants`, until Staff set its on-hand
Stock again. While Reservations hold some of it, it is a 409 whose `reserved` says how many; once
they are committed, released or expired, it can go. Its past Reservations stay.

The Reservation endpoints are internal: they need Checkout's own token, with the
`CHECKOUT` role ([ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)),
and the API gateway doesn't route them. On all of these a Customer's or the other role's token gets
403, and no token gets 401.

### Reservations

A Reservation holds a batch of Variants for one Customer, all or nothing:

```json
{"customerId": "…", "expiresAt": "2026-09-30T10:15:00Z",
 "items": [{"variantId": "PHN-PIXEL-9", "quantity": 2}]}
```

It applies in one transaction, whole or not at all, and can only hold available Stock:

- If any Variant has too little available, it is a 409 whose `insufficientStock` lists those Variants.
- If any Variant is unknown, it is a 404 whose `unknownVariants` lists them.
- An empty batch, a missing `variantId`, a quantity that isn't positive, a missing `customerId`, or
  an `expiresAt` that isn't in the future is a 400.

A Variant listed twice is reserved for the total. Every error is a problem detail. `expiresAt`
is kept to the microsecond. The 201 answers with `{"id", "customerId", "status", "expiresAt",
"items"}` and a `Location` of `/reservations/{id}`.

A Reservation is `ACTIVE`, `COMMITTED` or `RELEASED`. Commit and release take the owning Customer
as `{"customerId": "…"}` and answer with the Reservation. A Reservation that doesn't exist, or
belongs to another Customer, is a 404.

- **Commit** takes its units off on-hand Stock. Committing again changes nothing. Once its
  `expiresAt` has passed it is a 409 with `reservationExpired`, whether or not it was also
  released; a released Reservation that hasn't reached its `expiresAt` is a 409 with
  `reservationReleased`. Both name the Reservation's ID.
- **Release** gives its units back, and releasing again changes nothing. A committed Reservation
  can't be released: a 409 with `reservationCommitted`.

**Expiry.** An `ACTIVE` Reservation stops holding Stock the moment its `expiresAt` passes, so its
units are available again straight away. Every 30 seconds a sweeper marks expired `ACTIVE`
Reservations `RELEASED`; that is housekeeping, and nothing a caller sees depends on when it runs.
Set `ecomm.inventory.reservation-sweeper.enabled=false` to turn it off, as the tests do. The clock
is the `TimeSource` port.

[`http/inventory.http`](./http/inventory.http) exercises every endpoint against the compose stack.

## Storage

One `stock` row per Variant holds its `on_hand` count, with a check constraint keeping it
non-negative. A Reservation is a `reservation` row (owner, status, expiry) and one
`reservation_item` row per Variant. A `reservation_item` names its Variant without a foreign key
to `stock`, so a Reservation outlives the Stock it once held when Staff stop stocking the Variant. What a Variant has reserved is never stored: it is worked out
from its `ACTIVE` Reservations and the clock.

Every change that can take Stock (a Reservation, a commit, setting on-hand) first
locks its Variants' `stock` rows (`SELECT ... FOR UPDATE`, in Variant ID order so overlapping
batches can't deadlock), then reads what Reservations hold of them, checks the whole batch, and
writes it. So two Reservations of the last unit can't both succeed. Commit and release then lock
the Reservation's own row; the sweeper skips any Reservation that is locked.

## Seed data

`V2__seed_stock.sql` gives stock to the first Variant of each of Catalog's
[seed Products](../catalog/src/main/resources/seed/products.json), whose Variant ID is its SKU, and
`V3__seed_variant_stock.sql` to the extra Variants of its multi-Variant ones, one of them sold out.
They are migrations, so each runs once and a restart never undoes a sale; `ReadStockApiTest`
fails if they drift from Catalog's seed. To reset stock, drop and recreate the `inventory` database.

## Run it

```sh
docker compose up -d --build inventory   # from the repo root; listens on localhost:8082
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/inventory/`.

Or run Postgres and Keycloak in compose (`docker compose up -d keycloak`) and start the service
with `./gradlew :services:inventory:bootRun` on port 8080. Set `POSTGRES_PORT` for both when
something else holds 5432.
