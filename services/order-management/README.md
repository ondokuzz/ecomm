# Order Management

Orders from placement to delivery. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Data lives in the `orders` database on the shared Postgres,
with the schema under Flyway ([`db/migration`](./src/main/resources/db/migration)).

For Sprint 1 an Order is a plain row whose status is overwritten on each change. It is rebuilt as
CQRS + event sourcing in Sprint 3 (see the [roadmap](../../docs/roadmap.md)).

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
| `GET /orders` | Customer | The Customer's Orders, newest first |

An Order is placed for a Customer from the lines Checkout priced:

```json
{"customerId": "…",
 "lines": [{"variantId": "PHN-PIXEL-9", "quantity": 2,
            "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}]}
```

and comes back with its total, the sum of each line's unit price times its quantity:

```json
{"id": "…", "status": "PLACED",
 "lines": [{"variantId": "PHN-PIXEL-9", "quantity": 2,
            "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}],
 "total": {"amountMinor": 159800, "currency": "EUR"}, "placedAt": "2026-09-27T14:00:00.123456Z"}
```

An Order needs a `customerId` of at most 255 characters and at least one line. Each line needs a
`variantId` of at most 64 characters, a positive integer `quantity` and a `unitPrice` as `Money`
that isn't negative. Every line must be in the same currency, and a Variant may appear on only one
line. Anything else, including a total too large to hold, is a 400. Every error is a problem detail.

## Order Status

```
PLACED → PAID → FULFILLED → SHIPPED → DELIVERED
   ↓       ↓                             ↓
CANCELLED CANCELLED                   RETURNED
```

An Order can be cancelled until it is fulfilled, and returned once delivered. `CANCELLED` and
`RETURNED` are final. Any other change, including to the status it already has, is a 409. An unknown
status, or a missing or invalid `customerId`, is a 400. Two changes racing on the same Order can't
both win: the loser gets a 409.

[`http/order-management.http`](./http/order-management.http) exercises every endpoint against the
compose stack.

## Run it

```sh
docker compose up -d --build order-management   # from the repo root; listens on localhost:8085
```

Or run Postgres and Keycloak in compose (`docker compose up -d keycloak`) and start the service
with `./gradlew :services:order-management:bootRun` on port 8080. Set `POSTGRES_PORT` for both when
something else holds 5432.
