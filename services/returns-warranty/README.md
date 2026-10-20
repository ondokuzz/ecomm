# Returns & Warranty

RMAs, from a Customer's request to their resolution. A Customer requests one for units of a Variant
from a delivered Order: a return within the Return Period, or a warranty claim within the Warranty
Window, naming each unit's Serial number where the Product has Serial tracking. Staff approve or
reject it, once; receive its units by the Send-back deadline; and inspect them. Orchestration's
returns Saga then refunds, exchanges or closes it, puts it on hold, or expires it when its units
never came. Returns & Warranty checks each request against its own projections of Fulfillment &
Shipping's delivered Shipments, Catalog's Products and Order Management's Orders, keeps RMAs in its
own Postgres database, `returns_warranty`, and publishes each change as a `returns-warranty.rma`
event. Built from [`platform/service-template`](../../platform/service-template/README.md), so its
layout, security and testing conventions apply here.

The returns Saga that drives an RMA from approval to resolution, restocking, refunding and
replacing on the way, is Orchestration's (ticket #88); until it runs, its steps can be taken by hand
with Orchestration's token, as [the `.http` file](./http/returns-warranty.http) does.

## API

| Endpoint | Who | |
|---|---|---|
| `POST /rmas` | a Customer, with an `Idempotency-Key` | Request an RMA: 201, `REQUESTED` |
| `GET /rmas/eligibility?orderId=` | a Customer | What each line of their delivered Order is still eligible for |
| `GET /rmas/{id}` | the RMA's Customer, or Staff | Read it; anyone else's is a 404 |
| `GET /rmas?status=&orderId=&page=&size=` | a Customer, or Staff | A page of RMAs: a Customer's own, or for Staff everyone's (the RMA queue) |
| `POST /rmas/{id}/approve` | Staff | Approve it, with an optional `note` |
| `POST /rmas/{id}/reject` | Staff | Reject it, with a required `reason` |
| `POST /rmas/{id}/receive` | Staff, with an `Idempotency-Key` | Receive its units: `RECEIVED` |
| `POST /rmas/{id}/inspect` | Staff, with an `Idempotency-Key` | Record its Inspection: `INSPECTED` |
| `POST /rmas/{id}/resolve` | Orchestration, with an `Idempotency-Key` | Resolve it: `REFUNDED`, `EXCHANGED` or `CLOSED` |
| `POST /rmas/{id}/hold` | Orchestration, with an `Idempotency-Key` | Put it `ON_HOLD`, with a required `reason` |
| `POST /rmas/{id}/expire` | Orchestration, with an `Idempotency-Key` | Expire it once its Send-back deadline passed: `EXPIRED` |

The gateway routes all of them under `/api/returns-warranty/`; none is public. Resolving, holding
and expiring are internal: only the returns Saga calls them, so the gateway answers them 404.

A request names one Variant of one Order:

```json
{"orderId": "7f1c…", "variantId": "PHN-PIXEL-9-128-OBS", "quantity": 1,
 "serialNumbers": ["490154203237518"], "kind": "RETURN", "reason": "Not what I expected"}
```

`kind` is `RETURN` or `WARRANTY_CLAIM`, and `reason` is 1 to 1,000 characters. `serialNumbers` has
one per unit for a serial-tracked Variant, matched whatever their case, and is left out or empty for
any other. A malformed field is a 400 naming it in `errors`. Then:

| Answer | `reason` | When |
|---|---|---|
| 404 | | The Order has no delivered Shipment of the Customer's: someone else's, not delivered yet, or not projected yet |
| 422 | `quantityExceeded`, with `available` | More units than were delivered, less those the Customer's other RMAs for the Variant hold until they are rejected, expire or are closed. A Variant the Order didn't deliver has none |
| 422 | `serialNumbersMiscounted`, with `expected` | Not one Serial number per unit of a serial-tracked Variant, or any for another |
| 422 | `serialNotShipped`, with `serialNumber` | It didn't ship on the Order for that Variant |
| 422 | `serialAlreadyClaimed`, with `serialNumber` | Another RMA holds it, or the request names it twice. A `REJECTED`, `EXPIRED` or `CLOSED` RMA holds none, so a closed return's unit can still be claimed under warranty |
| 422 | `outsideReturnPeriod`, with `endedAt` | A return after the Return Period ended |
| 422 | `noWarranty` | A warranty claim on a Product without Warranty terms |
| 422 | `outsideWarrantyWindow`, with `endedAt` | A warranty claim after the Warranty Window ended |
| 503 | `productNotKnownYet`, with `Retry-After` | A warranty claim on a Variant whose Product isn't projected yet |
| 503 | `orderNotKnownYet`, with `Retry-After` | An Order that isn't projected yet, so its Refund amount can't be worked out |

A 4xx or 5xx isn't recorded under the `Idempotency-Key`, so the same key can be sent again.

An RMA reads as:

```json
{"id": "3f2a9c1b-…", "reference": "RMA-3F2A9C1B", "orderId": "7f1c…", "customerId": "…",
 "variantId": "PHN-PIXEL-9-128-OBS", "productName": "Pixel 9", "quantity": 1,
 "serialNumbers": ["490154203237518"], "kind": "WARRANTY_CLAIM", "reason": "It stopped charging",
 "window": {"kind": "WARRANTY_WINDOW", "startsAt": "2026-10-01T09:30:00Z",
            "endsAt": "2028-10-01T09:30:00Z", "warranty": {"months": 24, "manufacturer": "Google"}},
 "refundAmount": {"amountMinor": 79900, "currency": "EUR"},
 "status": "INSPECTED", "requestedAt": "2026-10-15T12:00:00Z",
 "sendBackBy": "2028-10-01T09:30:00Z", "warehouseId": "WH-BER",
 "inspection": {"outcome": "FAULT_CONFIRMED", "note": "Charging port worn"},
 "history": [{"status": "REQUESTED", "by": "CUSTOMER", "at": "2026-10-15T12:00:00Z"},
             {"status": "APPROVED", "by": "<Staff ID>", "at": "…", "note": "…"},
             {"status": "RECEIVED", "by": "<Staff ID>", "at": "…"},
             {"status": "INSPECTED", "by": "<Staff ID>", "at": "…", "note": "Charging port worn"}]}
```

- The **reference** is `RMA-` and the first eight characters of its ID, upper-cased, like an Order
  reference. It isn't stored.
- The **window** is the one it was checked against, kept on the RMA, so a later change to the
  Return Period or to the Product's Warranty terms doesn't change it. A Return Period has no
  `warranty`.
- The **Refund amount** is what refunding all its units gives back, worked out when it is requested
  ([below](#refund-amounts)). An RMA requested before Sprint 6 gains it once its Order is projected.
- The **status** moves as the [lifecycle](#the-lifecycle) allows. `sendBackBy` and `warehouseId`
  appear on approval; `inspection` once inspected; `resolution` once resolved, with the Refund's
  `paymentTransactionReference` or the `replacementShipmentId`.
- The **history** is its RMA history, oldest first, only ever appended to, in the transaction of the
  step: who took each step (a Staff ID, `CUSTOMER` for the request, `ORCHESTRATION` for the returns
  Saga's), when, and its note: an approval's, a rejection's reason, an Inspection's, a hold's
  reason. The RMA decisions are among its entries. An RMA requested before Sprint 6 has no entry for
  its request: its history starts with its RMA decision, kept as it was.

Deciding a decided RMA is a 409 `rmaAlreadyDecided`. A decision takes no `Idempotency-Key`: a repeat
is refused, not replayed. A listing's `page` counts from 0, and `size` is 1 to 100 (20 by default).
`status` takes one or more, repeated or comma-separated (`?status=APPROVED,RECEIVED`); an unknown one
is a 400. `APPROVED` alone is listed by Send-back deadline, soonest first, as Staff chase the units;
any other listing is oldest first.

### The lifecycle

```
REQUESTED → APPROVED | REJECTED
APPROVED  → RECEIVED | EXPIRED
RECEIVED  → INSPECTED
INSPECTED → REFUNDED | EXCHANGED | CLOSED | ON_HOLD
```

`REJECTED`, `EXPIRED`, `REFUNDED`, `EXCHANGED` and `CLOSED` are final, and nothing resolves a hold
yet. Any other step is a 409 `transitionRefused`, with the `status` the RMA is in, so two Staff
can't receive or inspect it twice and a receipt that raced the Send-back timer wins over `expire`.
Every step after a decision takes a required `Idempotency-Key`: a repeat with the same key replays
the first answer and changes nothing.

- **The Send-back deadline.** On approval, `sendBackBy` is the later of the window's end and
  `ecomm.returns-warranty.send-back-period` (14 days) after the approval, and `warehouseId` is the
  Warehouse its Shipment came from, where the units go back to.
- **Receiving**, `{"serialNumbers": [...]}` for a serial-tracked Variant or `{"quantity": n}` for
  any other, must be exactly the RMA's units, matched whatever their case: otherwise a 422
  `serialMismatch` naming, in `serialNumbers`, each that isn't the RMA's, or `quantityMismatch`
  with the `expected` number.
- **Inspecting**, `{"outcome", "note"?}`, takes `ACCEPTED` or `REFUSED` for a return and
  `FAULT_CONFIRMED` or `NO_FAULT_FOUND` for a warranty claim; the other kind's is a 422
  `outcomeNotForKind`. A refusing outcome (`REFUSED`, `NO_FAULT_FOUND`) needs a `note`, or it is a
  400 naming it.
- **Resolving**, `{"resolution", "paymentTransactionReference"?, "replacementShipmentId"?,
  "note"?}`, must follow from the Inspection, or it is a 422 `resolutionContradictsInspection`:
  `REFUNDED`, naming the Refund's Payment transaction, after an accepted return or a confirmed
  fault with no replacement; `EXCHANGED`, naming the replacement Shipment, after a confirmed fault;
  `CLOSED` after a refusing outcome. Its answer is the RMA with `orderFullyRefunded`: whether its
  `REFUNDED` RMAs now cover every unit the Order delivered, so the Saga can mark the Order
  `RETURNED`.
- **Holding**, `{"reason"}`, is for a declined Refund; **expiring** takes no body.

The returns Saga isn't built to the RMA's state: it takes these steps when their events tell it to,
and a step it repeats under its key is answered again.

### Eligibility

`GET /rmas/eligibility?orderId=` answers the Customer, for each line their delivered Order
delivered, whatever the windows say today:

```json
{"orderId": "7f1c…", "lines": [
  {"variantId": "PHN-PIXEL-9-128-OBS", "productName": "Pixel 9",
   "returnPeriodEndsAt": "2026-10-15T09:30:00Z", "warrantyWindowEndsAt": "2028-10-01T09:30:00Z",
   "serialNumbers": ["356938035643809"], "quantity": 1}]}
```

`warrantyWindowEndsAt` is left out when the Product has no Warranty terms, or isn't projected yet.
`serialNumbers` and `quantity` are what no RMA holds. Another Customer's Order, or one not
delivered, is a 404.

### Refund amounts

A unit's Refund amount is its Price, less its share of the Order's Discounts, plus its share of the
Order's Tax. The Shipping cost is never refunded. Each line gets a share in proportion to its
subtotal, by the largest-remainder rule (a cent left over goes to the line with the largest
remainder, a tie to the first), so the lines together come to the Order's total less Shipping. A
line's amount is then shared over its units in order, the first `n` coming to `floor(amount × n /
quantity)`, and an RMA is given what its units and those other RMAs hold come to, less what those
others were given. So the RMAs holding every unit come to the line's amount exactly, even after an
RMA was rejected, expired or closed and its units claimed again. It is the RMA's whole Refund, not
one unit's.

[`http/returns-warranty.http`](./http/returns-warranty.http) exercises it against the compose stack.

## Windows

Both windows start when the Shipment was delivered, and a request is within one until the instant
it ends. Neither limits the steps after an approval: the Send-back deadline says how long the units
may take.

- the **Return Period** lasts `ecomm.returns-warranty.return-period`, 14 days, one for the store;
- the **Warranty Window** lasts the Product's Warranty terms' months, counted on UTC's calendar: a
  delivery on 31 January with a one-month warranty ends on the last day of February, and a leap day
  ends on 28 February in a year without one.

A warranty claim may come before or after the Return Period ends; it is checked only against the
Warranty Window.

## The projections

Returns & Warranty consumes three topics, each in its own consumer group, and keeps the newest
version of each Shipment, Product and Order; a duplicate or an older event is ignored. Each event is applied in one
transaction with the version check. An event it can't apply goes to its group's dead-letter topic,
such as `returns-warranty.shipments.dlt`, after its retries, or at once if it can't be read, and
`make dlt-redrive GROUP=<group>` applies it once the cause is fixed
([service template](../../platform/service-template/README.md#consuming)).

| Topic | Group | Becomes |
|---|---|---|
| `fulfillment-shipping.shipment` | `returns-warranty.shipments` | A row in `shipment` per Shipment: its version, Order, Customer, Warehouse, status and `deliveredAt`; and a row in `shipment_line` per line, with its Product name, quantity, Serial tracking and Serial numbers |
| `catalog.product` | `returns-warranty.products` | A row in `product` per SKU: its version, Serial tracking and Warranty terms; and its Variant IDs in `product_variant` |
| `order-management.order` | `returns-warranty.orders` | A row in `customer_order` per Order: its version, Currency, and the sums of its Discounts and its Tax; and a row in `customer_order_line` per line, with its quantity and unit Price |

Only an Order's Shipment is projected: a replacement Shipment (`kind` `REPLACEMENT`) is skipped and
logged, as RMAs on replacement units are out of scope; an event without `kind` is an Order's. Only
a Shipment whose status is `DELIVERED` can be claimed against. The two projections are
independent, so a Shipment and its Products may arrive in either order: until a warranty claim's
Product arrives, the claim is a 503 to retry. A return needs no Product. A Product keeps every
Variant it has ever had, and events from before Warranty terms and Serial tracking read as none and
`NONE`. Statuses are read as text, so one Fulfillment & Shipping adds later doesn't fail the event.
An Order's events from before Discounts or Tax read as none. Each event logs a line: `Applied
Shipment 0b6f… version 6`, or `Ignored …`.

An Order's RMAs are checked one at a time, under a lock on its Shipment's row, so two requests can't
both claim the last unit or the same Serial number. An approval takes the same lock before the
RMA's.

### Upgrading from Sprint 5

`make up` alone upgrades a Sprint 5 stack. The V5 migration renames the decision history the RMA
history, its entries unchanged, and gives each `APPROVED` RMA its Send-back deadline, worked out
from its approval with the default 14 days, whatever `send-back-period` is set to. It may have
passed already, in which case the returns Saga expires the RMA at once.

Then the projections fill in what upgraded RMAs lack, in the transaction of the event that brings
it, each RMA under its lock. That is a change but not a step: the RMA's version goes up and it is
published, with the change `BACKFILLED`, but its history doesn't grow.

- **Warehouses.** Shipments kept before Sprint 6 have none. While any hasn't, the service reads
  `fulfillment-shipping.shipment` from the start whenever its partitions are assigned, and applies
  each such Shipment again at its version. Each gives its Order's approved RMAs its Warehouse.
- **Refund amounts.** `returns-warranty.orders` is a new group, so it reads its topic from the
  start. Each Order gives its RMAs without a Refund amount theirs, oldest first.

The returns Saga of an RMA approved in Sprint 5 may start before either arrives, from its
`APPROVED` event; the `BACKFILLED` events bring them after.

### Rebuilding

The projections can be rebuilt from the topics, which are compacted, at any time. From the repo
root:

```sh
make returns-rebuild
```

stops the service, resets its three consumer groups to the earliest offset, apart from their
redrive topics, which are moved to their end, empties the projections, and starts it again
([`replay-groups.sh`](../../infra/docker/replay-groups.sh) fails the command, before anything is
emptied, if Kafka refuses a reset). RMAs stay, with
the windows they were checked against and their Refund amounts. `make seed-reset` drops and recreates the whole
`returns_warranty` database instead, since the topics are emptied.

## Run it

```sh
docker compose up -d --build returns-warranty   # from the repo root; listens on localhost:8100
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/returns-warranty/`.

Or run Postgres, Keycloak and the event backbone in compose (`docker compose up -d postgres-databases
keycloak kafka-topics`) and start the service with `./gradlew :services:returns-warranty:bootRun` on
port 8080, with `SPRING_DATASOURCE_PASSWORD="$(cat .secrets/ecomm/postgres-password)"`: its
database password is a Compose secret, mounted as the file `spring.datasource.password` (see the
root README's "Secrets").

## Tests

- `RequestRmaApiTest`, `DecideRmaApiTest`, `RmaLifecycleApiTest`, `EligibilityApiTest`,
  `ReadRmasApiTest`, `RmaEventsApiTest`, `ProjectionApiTest` and `UpgradeApiTest` publish Shipment,
  Product and Order events as Fulfillment & Shipping, Catalog and Order Management would, each
  checked against its schema first (a delivered Shipment publishes its Order first, unless a test
  publishes its own), and use the API over HTTP with `FakeKeycloak` tokens: accepted returns and
  warranty claims, every refusal, Refund amounts, the recorded window surviving a Catalog change,
  decisions, receipts, Inspections, resolutions, holds and expiries with every refused step,
  idempotent repeats, eligibility, reads and listings by owner, Staff and status, events per change,
  projections by version in either order, replacement Shipments skipped, and the fill-ins of an
  upgraded stack, whose Sprint 5 rows `UpgradeApiTest` writes with SQL. The clock is a fixed `TestTimeSource`. Postgres, Kafka and the
  registry run in Testcontainers, each test topic with one partition, so a later event applied
  means the earlier ones are.
- `V5MigrationTest` migrates a database of its own to V4, writes Sprint 5's rows, migrates it to V5,
  and reads the tables: the migration's subject is the schema.
- `WindowTest`, `RmaRequestTest`, `RmaLifecycleTest` and `RefundAmountTest` are pure domain tests:
  the windows' arithmetic at month ends and leap days; which units and Serial numbers are free to
  claim; every transition and the Send-back deadline; and Refund amounts' largest-remainder
  allocation, exact once every unit is refunded.
- `ArchitectureTest` runs the shared `HexagonalRules`.
