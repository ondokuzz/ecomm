# Returns & Warranty

RMAs, from a Customer's request to Staff's RMA decision. A Customer requests one for units of a
Variant from a delivered Order: a return within the Return Period, or a warranty claim within the
Warranty Window, naming each unit's Serial number where the Product has Serial tracking. Staff
approve or reject it, once. Returns & Warranty checks each request against its own projections of
Fulfillment & Shipping's delivered Shipments and Catalog's Products, keeps RMAs in its own Postgres
database, `returns_warranty`, and publishes each change as a `returns-warranty.rma` event. Built
from [`platform/service-template`](../../platform/service-template/README.md), so its layout,
security and testing conventions apply here.

Nothing happens after an approval yet: receiving, inspecting, refunding and restocking, and the
returns Saga that drives them, are Sprint 6's. Nothing consumes the events yet either; Notifications
will in Sprint 6.

## API

| Endpoint | Who | |
|---|---|---|
| `POST /rmas` | a Customer, with an `Idempotency-Key` | Request an RMA: 201, `REQUESTED` |
| `GET /rmas/{id}` | the RMA's Customer, or Staff | Read it; anyone else's is a 404 |
| `GET /rmas?status=&orderId=&page=&size=` | a Customer, or Staff | A page of RMAs, oldest first: a Customer's own, or for Staff everyone's (the RMA approval queue) |
| `POST /rmas/{id}/approve` | Staff | Approve it, with an optional `note` |
| `POST /rmas/{id}/reject` | Staff | Reject it, with a required `reason` |

The gateway routes all of them under `/api/returns-warranty/`; none is public, and none internal.

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
| 422 | `quantityExceeded`, with `available` | More units than were delivered, less those the Customer's other RMAs for the Variant hold until they are rejected. A Variant the Order didn't deliver has none |
| 422 | `serialNumbersMiscounted`, with `expected` | Not one Serial number per unit of a serial-tracked Variant, or any for another |
| 422 | `serialNotShipped`, with `serialNumber` | It didn't ship on the Order for that Variant |
| 422 | `serialAlreadyClaimed`, with `serialNumber` | Another `REQUESTED` or `APPROVED` RMA has it, or the request names it twice |
| 422 | `outsideReturnPeriod`, with `endedAt` | A return after the Return Period ended |
| 422 | `noWarranty` | A warranty claim on a Product without Warranty terms |
| 422 | `outsideWarrantyWindow`, with `endedAt` | A warranty claim after the Warranty Window ended |
| 503 | `productNotKnownYet`, with `Retry-After` | A warranty claim on a Variant whose Product isn't projected yet |

A 4xx or 5xx isn't recorded under the `Idempotency-Key`, so the same key can be sent again.

An RMA reads as:

```json
{"id": "3f2a9c1b-…", "reference": "RMA-3F2A9C1B", "orderId": "7f1c…", "customerId": "…",
 "variantId": "PHN-PIXEL-9-128-OBS", "productName": "Pixel 9", "quantity": 1,
 "serialNumbers": ["490154203237518"], "kind": "WARRANTY_CLAIM", "reason": "It stopped charging",
 "window": {"kind": "WARRANTY_WINDOW", "startsAt": "2026-10-01T09:30:00Z",
            "endsAt": "2028-10-01T09:30:00Z", "warranty": {"months": 24, "manufacturer": "Google"}},
 "status": "APPROVED", "requestedAt": "2026-10-15T12:00:00Z",
 "decisions": [{"status": "APPROVED", "decidedBy": "<Staff ID>", "decidedAt": "…", "note": "…"}]}
```

- The **reference** is `RMA-` and the first eight characters of its ID, upper-cased, like an Order
  reference. It isn't stored.
- The **window** is the one it was checked against, kept on the RMA, so a later change to the
  Return Period or to the Product's Warranty terms doesn't change it. A Return Period has no
  `warranty`.
- The **status** is `REQUESTED`, then `APPROVED` or `REJECTED`, which is final this sprint.
- The **decisions** are its decision history, oldest first, only ever appended to. A rejection's
  `note` is its reason; an approval without a note has none.

Deciding a decided RMA is a 409 `rmaAlreadyDecided`. A decision takes no `Idempotency-Key`: a repeat
is refused, not replayed. A listing's `page` counts from 0, and `size` is 1 to 100 (20 by default);
an unknown `status` is a 400.

[`http/returns-warranty.http`](./http/returns-warranty.http) exercises it against the compose stack.

## Windows

Both windows start when the Shipment was delivered, and a request is within one until the instant
it ends:

- the **Return Period** lasts `ecomm.returns-warranty.return-period`, 14 days, one for the store;
- the **Warranty Window** lasts the Product's Warranty terms' months, counted on UTC's calendar: a
  delivery on 31 January with a one-month warranty ends on the last day of February, and a leap day
  ends on 28 February in a year without one.

A warranty claim may come before or after the Return Period ends; it is checked only against the
Warranty Window.

## The projections

Returns & Warranty consumes two topics, each in its own consumer group, and keeps the newest version
of each Shipment and Product; a duplicate or an older event is ignored. Each event is applied in one
transaction with the version check. An event it can't apply goes to its group's dead-letter topic,
such as `returns-warranty.shipments.dlt`, after its retries, or at once if it can't be read, and
`make dlt-redrive GROUP=<group>` applies it once the cause is fixed
([service template](../../platform/service-template/README.md#consuming)).

| Topic | Group | Becomes |
|---|---|---|
| `fulfillment-shipping.shipment` | `returns-warranty.shipments` | A row in `shipment` per Shipment: its version, Order, Customer, status and `deliveredAt`; and a row in `shipment_line` per line, with its Product name, quantity, Serial tracking and Serial numbers |
| `catalog.product` | `returns-warranty.products` | A row in `product` per SKU: its version, Serial tracking and Warranty terms; and its Variant IDs in `product_variant` |

Only a Shipment whose status is `DELIVERED` can be claimed against. The two projections are
independent, so a Shipment and its Products may arrive in either order: until a warranty claim's
Product arrives, the claim is a 503 to retry. A return needs no Product. A Product keeps every
Variant it has ever had, and events from before Warranty terms and Serial tracking read as none and
`NONE`. Statuses are read as text, so one Fulfillment & Shipping adds later doesn't fail the event.
Each event logs a line: `Applied Shipment 0b6f… version 6`, or `Ignored …`.

An Order's RMAs are checked one at a time, under a lock on its Shipment's row, so two requests can't
both claim the last unit or the same Serial number.

### Rebuilding

The projections can be rebuilt from the topics, which are compacted, at any time. From the repo
root:

```sh
make returns-rebuild
```

stops the service, empties the projections, resets both consumer groups to the earliest offset,
apart from their redrive topics, which are moved to their end, and starts it again. RMAs stay, with
the windows they were checked against. `make seed-reset` drops and recreates the whole
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

- `RequestRmaApiTest`, `DecideRmaApiTest`, `ReadRmasApiTest`, `RmaEventsApiTest` and
  `ProjectionApiTest` publish Shipment and Product events as Fulfillment & Shipping and Catalog
  would, each checked against its schema first, and use the API over HTTP with `FakeKeycloak`
  tokens: accepted returns and warranty claims, every refusal, the recorded window surviving a
  Catalog change, decisions and their history, reads by owner and Staff, events per change, and
  projections by version in either order. The clock is a fixed `TestTimeSource`. Postgres, Kafka
  and the registry run in Testcontainers, each test topic with one partition, so a later event
  applied means the earlier ones are.
- `WindowTest` and `RmaRequestTest` are pure domain tests: the windows' arithmetic at month ends
  and leap days, and which units and Serial numbers are free to claim.
- `ArchitectureTest` runs the shared `HexagonalRules`.
