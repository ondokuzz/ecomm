# Order Management

Orders from placement to delivery. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Data lives in the `orders` database on the shared Postgres,
with the schema under Flyway ([`db/migration`](./src/main/resources/db/migration)).

An Order keeps its current Order Status and its **Order Status history**: every Status it has been
in, when, and which caller moved it there. Every change appends to the history and publishes an
[Order event](#order-events) in the same transaction, through the outbox
([ADR 0002](../../docs/adr/0002-ledgers-and-outboxes-not-event-sourcing.md)). History entries are
never changed or deleted.

## API

Checkout places Orders and changes their status with its own token, with the `CHECKOUT` role
([ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)), naming the
Customer in the body. An Order belongs to that Customer, and they read it with their own token
(`CUSTOMER`), whose `sub` must match. Another Customer's Order is a 404, the same as an unknown one,
so its existence never leaks, and the same goes for a status change naming a Customer who doesn't
own the Order. Any other token gets 403, and no token gets 401. Staff have no Orders.

| Endpoint | Called by | |
|---|---|---|
| `POST /orders` | Checkout | Places an Order in `PLACED`; 201 with the Order, its URL in `Location` |
| `PATCH /orders/{id}/status` | Checkout | Moves the Order: `{"customerId", "status": "PAID"}`; 200 with the Order, 409 if illegal |
| `GET /orders/{id}` | Customer | The Customer's Order; 404 for an unknown ID or another Customer's |
| `GET /orders?page=0&size=20` | Customer | A page of the Customer's Orders, newest first, with their total |

An Order is placed for a Customer from the lines Checkout priced, an optional Discount and the tax:

```json
{"customerId": "…",
 "lines": [{"variantId": "PHN-PIXEL-9", "quantity": 2,
            "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}],
 "discount": {"couponCode": "WELCOME10", "amount": {"amountMinor": 15980, "currency": "EUR"}},
 "tax": {"amountMinor": 28764, "currency": "EUR"}}
```

and comes back with what it comes to. The `subtotal` is the sum of each line's unit price times its
quantity, and the `total` is the subtotal, less the discount, plus the tax: what Checkout authorizes
the Payment for.

```json
{"id": "…", "status": "PLACED",
 "lines": [{"variantId": "PHN-PIXEL-9", "quantity": 2,
            "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}],
 "subtotal": {"amountMinor": 159800, "currency": "EUR"},
 "discount": {"couponCode": "WELCOME10", "amount": {"amountMinor": 15980, "currency": "EUR"}},
 "tax": {"amountMinor": 28764, "currency": "EUR"},
 "total": {"amountMinor": 172584, "currency": "EUR"}, "placedAt": "2026-09-27T14:00:00.123456Z",
 "statusHistory": [{"status": "PLACED", "at": "2026-09-27T14:00:00.123456Z",
                    "changedBy": "CHECKOUT", "backfilled": false}]}
```

`statusHistory` is oldest first, and its last entry is the current `status`. `changedBy` is the
caller that made the change, `CHECKOUT` for now.

`GET /orders` pages the list: `page` counts from 0 (default 0) and `size` is 1 to 100 (default
20). Anything else is a 400. The page comes back with how many Orders the Customer has in all:

```json
{"items": [{"id": "…", "status": "PAID", "…": "…"}], "page": 0, "size": 20, "total": 37}
```

A page past the last has no items and the same total. **This changed in Sprint 3**: the response
used to be a bare array of every Order.

An Order without a Discount has `"discount": null`. Orders placed before Orders recorded their
Discount and tax read back with none and a zero tax, which is what they were charged.

An Order needs a `customerId` of at most 255 characters and at least one line. Each line needs a
`variantId` of at most 64 characters, a positive integer `quantity` and a `unitPrice` as `Money`
that isn't negative. Every line must be in the same currency, and a Variant may appear on only one
line. The `tax` is required, even when zero, and can't be negative. A `discount` needs a
`couponCode` of at most 64 characters and an `amount` that isn't negative. Both are in the lines'
currency, and the discount can bring the total to zero but not below. Anything else, including a
total too large to hold, is a 400. Every error is a problem detail.

## Order Status

```
PLACED → PAID → FULFILLED → SHIPPED → DELIVERED
   ↓       ↓                             ↓
CANCELLED CANCELLED                   RETURNED
```

An Order can be cancelled until it is fulfilled, and returned once delivered. `CANCELLED` and
`RETURNED` are final. Any other change, including to the status it already has, is a 409. An unknown
status, or a missing or invalid `customerId`, is a 400. Two changes racing on the same Order can't
both win: the loser gets a 409. Each change appends an entry to the Order Status history; a refused
one changes nothing and publishes nothing.

## Order events

Placing an Order and each Status change publish one `order-management.order` event, in the
transaction that makes the change. The event is keyed by the Order's ID and carries the Correlation
ID of the request as its `X-Correlation-Id` header. Its schema,
[`order-management.order.json`](../../platform/event-schemas/schemas/order-management.order.json),
is the definition:

```json
{"eventId": "…", "occurredAt": "2026-10-03T12:00:05Z", "orderId": "…", "version": 2,
 "change": "STATUS_CHANGED",
 "order": {"customerId": "…", "status": "PAID", "placedAt": "2026-10-03T12:00:00Z",
           "lines": [{"variantId": "PHN-PIXEL-9", "quantity": 1,
                      "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}],
           "discounts": [{"source": "COUPON", "couponCode": "WELCOME10",
                          "amount": {"amountMinor": 7990, "currency": "EUR"}}],
           "tax": {"amountMinor": 0, "currency": "EUR"},
           "subtotal": {"amountMinor": 79900, "currency": "EUR"},
           "total": {"amountMinor": 71910, "currency": "EUR"},
           "statusHistory": [{"status": "PLACED", "at": "2026-10-03T12:00:00Z", "changedBy": "CHECKOUT"},
                             {"status": "PAID", "at": "2026-10-03T12:00:05Z", "changedBy": "CHECKOUT"}]}}
```

- `change` is `PLACED`, `STATUS_CHANGED` or `BACKFILLED`.
- `version` is 1 when the Order is placed and goes up by one with every change. Consumers apply an
  event only when it is newer than what they hold.
- `discounts` is empty for an Order without a Discount. A history entry carries `"backfilled": true`
  only when it was reconstructed.

Watch the topic on the compose stack with:

```sh
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic order-management.order --from-beginning --property print.key=true --property print.headers=true
```

## Orders from before Sprint 3

Sprints 1 and 2 kept only an Order's current Status. On an existing stack, migration
`V4__order_status_history.sql` gives each Order a history. The history holds its placement at
`placedAt`, and, when it has moved on, its current Status marked `backfilled`, also at `placedAt`,
because the real time was never recorded. Whatever lay between is lost: a cancelled Order shows
Placed, then Cancelled, whether or not it was paid.

When the service starts, a one-time job publishes each of those Orders once, with `change:
BACKFILLED`, so consumers start complete. It works in batches, each publishing its Orders and marking
them done in one transaction, so a restart publishes nothing more.

[`http/order-management.http`](./http/order-management.http) exercises every endpoint against the
compose stack.

## Run it

```sh
docker compose up -d --build order-management   # from the repo root; listens on localhost:8085
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/order-management/`.

Or run Postgres and Keycloak in compose (`docker compose up -d keycloak`) and start the service
with `./gradlew :services:order-management:bootRun` on port 8080. Set `POSTGRES_PORT` for both when
something else holds 5432.
