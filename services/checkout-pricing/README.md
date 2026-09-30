# Checkout & Pricing

Turns a Customer's Cart into a paid Order in two steps: a **Checkout Session** holds the Cart for
15 minutes, at the Prices of the moment it started, and paying the session buys it. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Checkout Sessions live in Redis; otherwise it holds nothing
but its own cached token. Why sessions hold Stock through Inventory's Reservations is in
[ADR 0001](./docs/adr/0001-checkout-sessions-hold-stock-through-reservations.md).

## API

Every endpoint is the calling Customer's own and needs a `CUSTOMER` token.

| Endpoint | |
|---|---|
| `POST /checkout/sessions` | Starts a Checkout Session for the caller's Cart; 201 with the session and its `Location` |
| `GET /checkout/sessions/current` | The caller's live Checkout Session, or 404 |
| `POST /checkout/sessions/{id}/pay` | Pays it; 200 with `{"orderId": "…", "status": "PAID"}` |

A session looks like this. Its `total` is the `subtotal` plus the `tax`:

```json
{"id": "…", "expiresAt": "2026-09-30T10:15:00Z",
 "lines": [{"variantId": "PHN-PIXEL-9", "quantity": 2,
            "unitPrice": {"amountMinor": 79900, "currency": "EUR"},
            "lineTotal": {"amountMinor": 159800, "currency": "EUR"}}],
 "subtotal": {"amountMinor": 159800, "currency": "EUR"},
 "tax": {"amountMinor": 0, "currency": "EUR"},
 "total": {"amountMinor": 159800, "currency": "EUR"}}
```

### Starting a session

1. read the Cart;
2. price every line from its Variant in Catalog (`GET /variants/{variantId}`), ignoring any price
   the Cart shows, and work out the tax;
3. if the Customer already has a session, release its Reservation and drop it: a Customer has at
   most one;
4. reserve the Cart's Stock in Inventory (`POST /reservations`) until 2 minutes after the session
   expires, so a payment started in the session's last moments can still commit it;
5. keep the session for 15 minutes, with the Reservation's ID, and return it.

The Prices captured here are the ones the Customer pays for the session's whole life, even if
Catalog changes them meanwhile.

### Paying a session

A session that has expired is a 410, and one that doesn't exist, has ended or belongs to someone
else is a 404; in both cases nothing else happens. Otherwise, in order:

1. place the Order in `PLACED` in Order Management, at the session's Prices, with its tax;
2. authorize the payment for the Order's total (lines plus tax), as Order Management answers it;
3. commit the Reservation, which takes its Stock off on-hand for good;
4. set the Order to `PAID`;
5. clear the Cart;
6. end the session;
7. return the Order's ID and Order Status.

Tax comes from the `TaxCalculator` port. For now that is `ZeroTaxCalculator`
([ADR 0005](../../docs/adr/0005-localization-compliance-abstracted.md)). It is worked out when the
session starts, sent on `POST /orders` even when zero, and the Order records it. The Payment is
authorized for the total Order Management gives the Order, never one Checkout works out itself, so
the two always match. Checkout applies no Discount yet.

### Errors

Every error is a problem detail.

| Status | When |
|---|---|
| 400 | Starting: the Cart is empty |
| 401 / 403 | No token / not a Customer (Staff and services have no Cart) |
| 404 | No live session (`current`), or paying one the Customer doesn't have |
| 409 | Starting: `unknownVariants`: Catalog no longer has these Variants. `outOfStock`: Inventory can't hold these. Also a Cart priced in more than one currency |
| 410 | Paying a session that has expired |
| 502 | Another service failed or answered unexpectedly |
| 503 | Keycloak couldn't issue Checkout's own token |

Starting a session changes nothing when it fails, except that a replaced session is gone and its
Stock released. Once paying has placed the Order, a failing step sets it to `CANCELLED` and leaves
the Cart and the session as they were. That's the only compensation for now: if the commit fails
after the Payment is authorized, the Payment stays authorized, the same known limitation as Sprint
1, which the Sagas in Sprint 3 fix. A Cart that can't be cleared, or a session that can't be ended,
after the Order is paid is only logged, since the Customer keeps the paid Order either way.

## Checkout Sessions

A session is one JSON string in Redis, `checkout-session:<ID>`, and the Customer's pointer to it,
`checkout-customer:<Customer ID>`, holds its ID. Both are written in one script with a TTL that
runs to the Reservation's expiry, 2 minutes after the session's. The session's own `expiresAt`, on
Checkout's clock (the `TimeSource` port), is what decides it has expired: from then on it is no
longer `current`, and paying it is a 410. Once Redis drops it, paying it is a 404, like a session
that never existed. Ending a session deletes both keys, unless the pointer already names a newer
session.

A session that expires unpaid leaves its Reservation to Inventory, which stops holding the Stock
at the Reservation's own `expiresAt`, 2 minutes later.

## Outbound identity

Checkout calls each service with the identity that service expects
([ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)):

- **Cart** (read, clear): the Customer's own JWT, forwarded as is. The Cart adapter takes it from
  the current request's security context, so the use case and its ports only ever see the
  Customer ID.
- **Catalog**: no token; reads are public.
- **Inventory, Order Management, Payment**: Checkout's own token, from the confidential `checkout`
  client (client credentials, `CHECKOUT` role). The Customer goes in the body as `customerId`, on
  `POST /reservations` and its commit and release, `POST /orders`, every
  `PATCH /orders/{id}/status` (the compensating `CANCELLED` too), and `POST /payments`.

The client secret comes from `CHECKOUT_CLIENT_SECRET`, which compose sets; there is no default.

Every call, to every service, also carries the Correlation ID of the request it serves in
`X-Correlation-Id`, and Checkout logs each one with its status and duration. So each step of a
checkout can be followed through all their logs, Catalog and Cart reads included (see the
[service template](../../platform/service-template/README.md#correlation-ids-and-logs)).

Spring Security's OAuth2 client fetches the token and caches it in memory. It fetches a new one
before any call when the cached one has less than 60 seconds left. On a 401 from a downstream
service, the cached token is dropped and the call is retried exactly once with a fresh one. That
is safe even for a `POST`, because a resource server rejects a bad token before any handler runs.
No other status is retried.

If Keycloak is unreachable when a session starts, or before the Order is placed, the step fails
with a 503. If it becomes unreachable afterwards, the compensating `CANCELLED` can't be sent
either, and the Order stays `PLACED`. That's a known Sprint 1 limitation, fixed by the Saga work.

[`http/checkout-pricing.http`](./http/checkout-pricing.http) checks out the demo Customer's Cart
against the compose stack.

## Run it

```sh
docker compose up -d --build checkout-pricing   # from the repo root; listens on localhost:8086
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/checkout-pricing/`.

Or run the rest of the stack in compose, Redis included, and start the service with
`CHECKOUT_CLIENT_SECRET=checkout-dev-secret ./gradlew :services:checkout-pricing:bootRun` on port
8080. It finds the other services on their compose host ports.
