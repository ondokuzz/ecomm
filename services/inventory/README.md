# Inventory

Stock levels per Variant. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Data lives in the `inventory` database on the shared Postgres,
with the schema under Flyway ([`db/migration`](./src/main/resources/db/migration)), and Stock reads
are served from a [cache](#the-stock-cache) in the stack's Redis.

Stock is counted per Variant as **on-hand** units, some of which Reservations may hold. What is
left is available to sell. Checkout takes Stock only through Reservations: a Checkout Session
reserves the Cart's Stock, and paying it commits the Reservation
([Checkout ADR 0001](../checkout-pricing/docs/adr/0001-checkout-sessions-hold-stock-through-reservations.md)).
Every change to On-hand, or to what Reservations hold, is recorded as a Stock movement in a ledger
beside the Stock counter, so On-hand can be checked against it, and is published as an
`inventory.stock` event ([ADR 0002](../../docs/adr/0002-ledgers-and-outboxes-not-event-sourcing.md)).

## API

| Endpoint | Who | |
|---|---|---|
| `GET /stock/{variantId}` | anyone | `{"variantId", "quantity", "onHand", "reserved"}`; 404 for an unknown Variant |
| `GET /stock?variantIds=a,b,c` | anyone | A list of those Variants' Stock, as above, in the order asked, each once; unknown ones are left out. 1 to 50 distinct IDs, comma-separated or repeated, or it is a 400 |
| `PUT /stock/{variantId}` | Staff | Sets on-hand Stock: 201 for a new Variant, 200 otherwise |
| `GET /stock/{variantId}/movements` | Staff | A page of the Variant's Stock movements, newest first, and whether On-hand equals their sum |
| `DELETE /stock/{variantId}` | Staff | Stops stocking a Variant: 204; 404 for an unknown Variant, 409 while Reservations hold some |
| `POST /stock/{variantId}/restocks` | Orchestration | Puts returned units back on hand, naming the RMA: 200 with the Stock. Takes an `Idempotency-Key` |
| `POST /reservations` | Checkout, Orchestration | Holds a batch for a Customer, or for an RMA's replacement, until `expiresAt`; 201 with the Reservation |
| `POST /reservations/{id}/commit` | Orchestration | Takes the held Stock off on-hand for good |
| `POST /reservations/{id}/release` | Checkout | Gives the held Stock back |

`quantity` is what is available to sell: `onHand` less what `ACTIVE` Reservations that haven't
expired yet hold (`reserved`). Both reads are served from the [Stock cache](#the-stock-cache); the
gateway routes both without a token.

Setting on-hand Stock needs a Staff token (`STAFF` role). It takes `{"onHand": 12}`, and
optionally a `reason` of up to 200 characters (`{"onHand": 12, "reason": "stocktake"}`), and adds
the Variant if Inventory doesn't stock it yet. Setting it below `reserved` is a 409 whose `reserved`
says how many units Reservations hold; a negative or non-integer count, or a reason that is too
long, is a 400.

The returns Saga restocks the units of an accepted return with Orchestration's own token
(`ORCHESTRATION`), which only it may do: `{"quantity": 1, "reference": "RMA-3F2A9C1B"}`. It
records a `RESTOCKED` movement naming the reference, raises On-hand, publishes the Variant's Stock
with change `RESTOCKED`, and the Stock cache follows, as for every change. It answers 200 with the
Stock, as `GET /stock/{variantId}` does. It needs an `Idempotency-Key` (400
`idempotencyKeyRequired` without one); the same key again replays the first answer, with
`Idempotent-Replayed: true`, and restocks nothing more. A Variant is also restocked once per
reference, whatever the key: a restock naming a reference the Variant has a `RESTOCKED` movement for
answers 200 with the Stock as it is, recording and publishing nothing. So a second run of a returns
Saga, whose keys are its own, restocks an RMA's units once. It is checked under the Variant's stock
row lock, so two such restocks at once can't both pass. A quantity below 1, or a reference that is
missing or longer than 64 characters, is a 400; a Variant Inventory doesn't stock is a 404 with
`unknownVariants`. Staff, Checkout and Customers get 403.

Staff stop stocking a Variant when its Product leaves the Catalog; the
[Admin Console](../../frontend/admin-console/README.md) does it when it deletes a Product. Its
stock is then a 404, and reserving it is a 404 with `unknownVariants`, until Staff set its on-hand
Stock again. While Reservations hold some of it, it is a 409 whose `reserved` says how many; once
they are committed, released or expired, it can go. Its past Reservations stay.

The Reservation endpoints are internal, and the API gateway doesn't route them. Reserving and
releasing need Checkout's own token, with the `CHECKOUT` role
([ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)); the
checkout Saga commits a Reservation with Orchestration's own token (`ORCHESTRATION`), which only it
may do since Checkout moved onto the Saga (#60). The returns Saga reserves and commits a
replacement unit with Orchestration's token too, for an RMA ([below](#reservations-for-an-rma)).
Committing takes no
`Idempotency-Key`: committing a `COMMITTED` Reservation again changes nothing and answers the same
200. On all of these a Customer's or the other role's token gets
403, and no token gets 401.

### Reservations

A Reservation holds a batch of Variants for one owner, all or nothing. The owner is a Customer, or
an RMA ([below](#reservations-for-an-rma)), named in `customerId`:

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

### Reservations for an RMA

The returns Saga holds the replacement unit of a warranty claim with a Reservation owned by the
RMA: its `customerId` is `rma:<rmaId>`, and it commits it with the same owner. Checkout reserves
only for Customers and Orchestration only for RMAs; reserving for the other kind of owner is a 403.
Checkout can't release an RMA's Reservation either (403), and Orchestration releases none.

- An RMA has at most one Reservation not yet released. Asking again for an RMA whose Reservation
  is committed, or still holds its Stock, answers it with 200, holding nothing more, so a retried
  step reserves once. A unique index on the owner backs this up.
- One that expired before it was committed, with Orchestration down meanwhile, can never be
  committed. Asking again releases it, recording its `RELEASED` movements as the sweeper would,
  and makes a new one: 201.
- Too little Stock is the 409 with `insufficientStock`, which the returns Saga reads as "replace
  with a Refund".
- Its `RESERVED`, `COMMITTED` and `RELEASED` movements name the owner as their `reference`.

**Expiry.** An `ACTIVE` Reservation stops holding Stock the moment its `expiresAt` passes, so its
units are available again straight away. Every 30 seconds a sweeper marks expired `ACTIVE`
Reservations `RELEASED`, recording their `RELEASED` movements and publishing their Variants' Stock.
Nothing a caller reads from the API depends on when it runs; the ledger and the events wait for it.
Set `ecomm.inventory.reservation-sweeper.enabled=false` to turn it off, as the tests do. The clock
is the `TimeSource` port.

### Stock movements

Every change to a Variant's On-hand units, or to what Reservations hold of them, records Stock
movements in the same transaction. Movements are never changed or deleted. Each says by how much
it changed On-hand (`onHandChange`) and what Reservations hold (`reservedChange`):

| Kind | When | `onHandChange` | `reservedChange` |
|---|---|---|---|
| `ADJUSTED` | Staff set On-hand: the difference, with their `reason`. Stopping stocking a Variant takes it to 0, with reason `stopped stocking` | ± the difference | 0 |
| `RESERVED` | A Reservation is made, one per Variant | 0 | + its quantity |
| `RELEASED` | A Reservation is released, by Checkout or by the sweeper once it has expired | 0 | − its quantity |
| `COMMITTED` | A Reservation is committed | − its quantity | − its quantity |
| `RESTOCKED` | The returns Saga puts a return's units back on hand, naming its RMA reference | + its quantity | 0 |
| `RECEIVED` | Units arrive from a supplier. Unused until replenishment exists | + | 0 |

Setting On-hand to what it already is, committing or releasing a Reservation again, and anything
refused record nothing. A movement made by a Reservation names it as `reservationId`; otherwise,
like a missing `reason`, it is `null`. `reference` is what it was for: a restock's RMA reference,
or `rma:<rmaId>` for the movements of an RMA's Reservation; it is `null` for every other movement,
and for every movement recorded before Sprint 6.

`GET /stock/{variantId}/movements?page=0&size=20` (Staff only; `size` up to 100) answers:

```json
{"variantId": "PHN-PIXEL-9", "onHand": 23, "onHandFromMovements": 23, "balanced": true,
 "items": [{"kind": "COMMITTED", "onHandChange": -2, "reservedChange": -2,
            "reservationId": "…", "reason": null, "reference": "rma:…",
            "at": "2026-10-05T10:00:00Z"},
           {"kind": "ADJUSTED", "onHandChange": 25, "reservedChange": 0,
            "reservationId": null, "reason": "opening balance", "reference": null,
            "at": "2026-10-04T09:00:00Z"}],
 "page": 0, "size": 20, "total": 2}
```

`onHandFromMovements` is the sum of every `onHandChange`, and `balanced` says whether it equals
`onHand`; both are read in one snapshot. An unknown Variant is a 404, Customers and Checkout get
403, and no token gets 401. The gateway routes it, with a token.

What Reservations hold isn't checked the same way: a Reservation stops holding Stock the moment it
expires, but its `RELEASED` movement waits for the sweep.

## The Stock cache

Stock reads are served from a copy of each Variant's Stock in the stack's Redis, so a busy
Storefront doesn't put its reads on Postgres beside Checkout's Reservations. Postgres stays the only
authority: the cache only spares it reads, and Reservations that can't succeed.

- **Entries.** One JSON string per Variant, `inventory:stock:<Variant ID>`: its `onHand`,
  `reserved`, `available`, whether it is `stocked`, and its Stock `version`, the one its last
  [Stock event](#stock-events) carried. A Variant Staff stopped stocking has an entry with `stocked`
  false, so it reads as a 404 from the cache too.
- **Writes.** Every change to Stock writes the entry of each Variant it touched once its
  transaction commits. A Lua script compares versions, so a write that lands late never overwrites
  a newer one. A read that misses fills the entry from Postgres the same way. The
  startup backfill writes nothing: it changes no counts.
- **Time to live.** An entry lives for the earlier of 60 seconds and the soonest `expiresAt` of
  the active Reservations holding some of the Variant, and is trusted only until then by
  Inventory's own clock. So Stock an expiring Reservation gives back is available from the cache
  the moment it expires, as from Postgres, before any sweep.
- **Reservations.** A Reservation first reads the cache. If it shows less available of some
  Variant than the batch asks for, it is a 409 with `insufficientStock`, without a Postgres
  transaction. Otherwise the Reservation runs in Postgres as above, and Postgres decides.
- **Staff.** Setting On-hand, removing a Variant and reading its Stock movements always use
  Postgres.
- **Redis down.** The cache is never required. A call to Redis that fails, which gives up within
  about 250 ms, is logged at warn, and reads and Reservations go to Postgres. Redis doesn't count
  in Inventory's health.

Inventory counts `ecomm_stock_cache_hits_total` and `ecomm_stock_cache_misses_total` per Variant
looked up, by a Stock read or a Reservation's check, and `ecomm_stock_cache_errors_total` per failed call to Redis. Grafana's **Services**
dashboard shows the hit ratio and all three.

## Stock events

Each change publishes one `inventory.stock` event per Variant it touched, in its transaction. The
event is keyed by the Variant's ID and carries the Correlation ID of the request as its
`X-Correlation-Id` header. Its schema,
[`inventory.stock.json`](../../platform/event-schemas/schemas/inventory.stock.json), is the
definition:

```json
{"eventId": "…", "occurredAt": "2026-10-05T10:00:00Z", "variantId": "PHN-PIXEL-9",
 "version": 42, "change": "RESERVED",
 "stock": {"onHand": 25, "available": 23, "stocked": true}}
```

- `change` is the kind of movement, `REMOVED` when Staff stop stocking the Variant, or
  `BACKFILLED`.
- `available` is `quantity` in `GET /stock/{variantId}`. The `REMOVED` event has `stocked: false`
  and 0 for both counts.
- `version` goes up with every change. All Variants draw it from one sequence, so it isn't
  contiguous per Variant, and a Variant stocked again after its removal still moves on from its
  last version. Consumers apply an event only when it is newer than what they hold.
- A Reservation's expiry changes `available` at once, but its event is published by the next
  sweep, at most 30 seconds later. Search's "in stock" can lag by that much.
- A refused change, such as a Reservation of more than is available, publishes nothing.

Watch the topic on the compose stack with:

```sh
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic inventory.stock --from-beginning --property print.key=true --property print.headers=true
```

### Backfill

A stack from Sprint 2 had Stock but no ledger. Migration `V7` gives each Variant an `ADJUSTED`
movement of its On-hand, and each line of every `ACTIVE` Reservation, expired or not, a `RESERVED`
movement, all with reason `opening balance`. Expired ones are then released by the next sweep like
any other. On start, a job publishes every Variant stocked before Stock events once, with change
`BACKFILLED`, so consumers start complete. Each is published in the transaction that marks it done,
so a restart, or a second instance, publishes nothing more.

[`http/inventory.http`](./http/inventory.http) exercises every endpoint against the compose stack.

## Storage

One `stock` row per Variant holds its `on_hand` count, with a check constraint keeping it
non-negative. A Reservation is a `reservation` row (owner, status, expiry) and one
`reservation_item` row per Variant. A `reservation_item` names its Variant without a foreign key
to `stock`, so a Reservation outlives the Stock it once held when Staff stop stocking the Variant. What a Variant has reserved is never stored: it is worked out
from its `ACTIVE` Reservations and the clock.

Every change to Stock (a Reservation, a commit, a release, a sweep, setting on-hand) first locks
its Variants' `stock` rows (`SELECT ... FOR UPDATE`, in Variant ID order so overlapping batches
can't deadlock), then reads what Reservations hold of them, checks the whole batch, and writes it.
So two Reservations of the last unit can't both succeed. Commit and release then lock the
Reservation's own row; the sweeper skips any Reservation that is locked.

Each `stock` row also holds the `version` of its last event, taken from the `stock_version`
sequence on every write. Stock movements are `stock_movement` rows, only ever inserted and read
newest first by their generated ID; like `reservation_item`, they name their Variant without a
foreign key. Spring Modulith's `event_publication` table is the outbox, and
`stock_awaiting_backfill_event` lists the Variants the startup job has still to publish.
`idempotency_key` holds the keys of restocks, for service-commons' `@IdempotentCommand`, and a
partial unique index on `reservation (customer_id)` keeps an RMA to one Reservation not yet
released.

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

Or run Postgres, Redis and Keycloak in compose (`docker compose up -d redis keycloak`) and start
the service with `./gradlew :services:inventory:bootRun` on port 8080. Set `POSTGRES_PORT` (or
`REDIS_PORT`) for both when something else holds 5432 (or 6379).

Since Sprint 5 its database password is a Compose secret, mounted as the file
`spring.datasource.password` (see the root README's "Secrets"); `application.yml` has none. Outside
Docker, give it as `SPRING_DATASOURCE_PASSWORD="$(cat .secrets/ecomm/postgres-password)"`.
