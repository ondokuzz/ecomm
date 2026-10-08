# Checkout & Pricing

Turns a Customer's Cart into a paid Order in two steps: a **Checkout Session** holds the Cart for
15 minutes, at the Prices of the moment it started, with every running **Campaign**'s Discount it is
due, and may take a **Coupon** on top; paying the session buys it. Built from
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
| `PUT /checkout/sessions/{id}/coupon` | Applies a Coupon with `{"code": "WELCOME10"}`, in place of any it had; 200 with the session |
| `DELETE /checkout/sessions/{id}/coupon` | Takes the Coupon off, if any; 200 with the session |
| `POST /checkout/sessions/{id}/pay` | Pays it with `{"paymentMethod": "…"}`; 200 with `{"orderId": "…", "status": "PAID"}` |

A session looks like this. `discounts` lists every Discount in the order they apply, Promotions'
answer: the running Campaigns', by ID and name, then the Coupon's, by its code, once one is applied.
Its `total` is the `subtotal`, less every Discount, plus the `tax`:

```json
{"id": "…", "expiresAt": "2026-09-30T10:15:00Z",
 "lines": [{"variantId": "AUD-AIRPODS-PRO-2", "quantity": 1,
            "unitPrice": {"amountMinor": 27900, "currency": "EUR"},
            "lineTotal": {"amountMinor": 27900, "currency": "EUR"}}],
 "subtotal": {"amountMinor": 27900, "currency": "EUR"},
 "discounts": [
   {"source": "CAMPAIGN", "couponCode": null, "campaignId": "b4a50413-…",
    "campaignName": "Audio week", "amount": {"amountMinor": 4185, "currency": "EUR"}},
   {"source": "COUPON", "couponCode": "WELCOME10", "campaignId": null, "campaignName": null,
    "amount": {"amountMinor": 2371, "currency": "EUR"}}],
 "tax": {"amountMinor": 0, "currency": "EUR"},
 "total": {"amountMinor": 21344, "currency": "EUR"}}
```

### Starting a session

1. read the Cart;
2. price every line from its Variant in Catalog (`GET /variants/{variantId}`), ignoring any price
   the Cart shows, and note its Product's SKU and Category;
3. ask Promotions for every Discount the lines are due with no Coupon: the running Campaigns'
   (`POST /discounts/evaluate`, with Checkout's own token), and work out the tax on the subtotal less
   them. This comes before any Stock is held, so a Promotions failure holds none;
4. if the Customer already has a session, release its Reservation and drop it: a Customer has at
   most one;
5. reserve the Cart's Stock in Inventory (`POST /reservations`) until 2 minutes after the session
   expires, so a payment started in the session's last moments can still commit it;
6. keep the session for 15 minutes, with the Reservation's ID, and return it.

The Prices captured here are the ones the Customer pays for the session's whole life, even if
Catalog changes them meanwhile.

### Applying a Coupon

`PUT /checkout/sessions/{id}/coupon` with `{"code": "welcome10"}` asks Promotions again for
every Discount the session's lines are due, this time with the Coupon (`POST /discounts/evaluate`,
with Checkout's own token). Each line goes with its Variant, its Product's SKU and Category, its
quantity and its captured unit Price, and Promotions answers with the running Campaigns' Discounts,
each on what its lines still come to, then the Coupon's on what is left, under its upper-case code
([Promotions](../promotions/README.md#api)). Checkout keeps that whole list in place of the session's
own, so a Campaign that started or ended since the session began is counted as it stands now, works
out the tax again on the subtotal less every Discount, and keeps the session with both for the rest
of its life: its `expiresAt` doesn't move. A session holds one Coupon, so applying another replaces
the first.

Checkout checks Promotions' answer before keeping it: every Discount names a Campaign or a Coupon,
is in the session's currency and not negative, together they come to no more than the subtotal, and
a Coupon's comes last exactly when a Coupon was asked about. Anything else is a 502, and the session
stays as it was.

A Coupon that doesn't apply is a 422 whose `reason` is Promotions' own: `unknown`, `inactive`,
`notYetValid`, `expired`, `belowMinimum` or `currencyMismatch`. The session is left as it was,
with any Coupon it already had. A missing, blank or non-string `code`, or one past 64 characters,
is a 400 and never reaches Promotions.

`DELETE /checkout/sessions/{id}/coupon` takes the Coupon off: it asks Promotions again with no
Coupon, keeps the Campaigns' Discounts it answers, and works out the tax again. Both answer with the session, and, like paying, are a 410
once the session has expired and a 404 for a session the Customer doesn't have, asking Promotions
nothing.

The Discounts are Promotions' answer when the session started or its Coupon last changed, and
paying honours them just as it honours the session's Prices, even if a Campaign or the Coupon ends
in the meantime.

### Paying a session

The body names the Payment method, `{"paymentMethod": "tok_approve"}`: an opaque token from the
payment gateway, which Checkout passes on to Payment unread (the mock's test tokens are in
[Payment's README](../payment/README.md#gateways)). A missing, blank or non-string one, or one past
255 characters, is a 400. A session that has expired is a 410, and one that doesn't exist, has ended
or belongs to someone else is a 404; in all three cases nothing else happens. Otherwise, in order:

1. place the Order in `PLACED` in Order Management, at the session's Prices, with every Discount in
   order (`"discounts"`, possibly empty) and its tax;
2. authorize the payment for the Order's total (lines less every Discount, plus tax), as Order
   Management answers it, with the Payment method and the `Idempotency-Key`
   `checkout:<orderId>:authorize`, which Payment requires;
3. commit the Reservation, which takes its Stock off on-hand for good;
4. set the Order to `PAID`;
5. clear the Cart;
6. end the session;
7. return the Order's ID and Order Status.

Tax comes from the `TaxCalculator` port, on the subtotal less every Discount. For now that is
`ZeroTaxCalculator` ([ADR 0005](../../docs/adr/0005-localization-compliance-abstracted.md)). It is
worked out when the session starts and again whenever its Coupon changes, sent on `POST /orders`
even when zero, and the Order records it. The Payment is authorized for the total Order Management
gives the Order, never one Checkout works out itself, so the two always match.

### Errors

Every error is a problem detail.

| Status | When |
|---|---|
| 400 | Starting: the Cart is empty. Paying: no usable `paymentMethod`. Applying a Coupon: no usable `code` |
| 401 / 403 | No token / not a Customer (Staff and services have no Cart) |
| 404 | No live session (`current`), or paying or changing the Coupon of one the Customer doesn't have |
| 409 | Starting: `unknownVariants`: Catalog no longer has these Variants. `outOfStock`: Inventory can't hold these. Also a Cart priced in more than one currency |
| 402 | Paying: the gateway declined the payment; `declineReason` gives its reason, such as `insufficient_funds` |
| 422 | Applying a Coupon that doesn't apply; `reason` says why, as Promotions does |
| 410 | Paying, or changing the Coupon of, a session that has expired |
| 502 | Another service failed or answered unexpectedly, the payment gateway failing to answer included, or Promotions answered Discounts that can't be right |
| 503 | Keycloak couldn't issue Checkout's own token |

Starting a session changes nothing when it fails, except that a replaced session is gone and its
Stock released. Once paying has placed the Order, a failing step sets it to `CANCELLED` and leaves
the Cart and the session as they were. A declined payment (402) and a gateway failure (502) are
such steps: the session and its Reservation stay, so the Customer can pay it again, with another
Payment method, until it expires. Each attempt places a new Order. That's the only compensation for now: if the commit fails
after the Payment is authorized, the Payment stays authorized, the same known limitation as Sprint
1, which the checkout Saga fixes in Sprint 4. A Cart that can't be cleared, or a session that can't be ended,
after the Order is paid is only logged, since the Customer keeps the paid Order either way.

## Checkout Sessions

A session is one JSON string in Redis, `checkout-session:<ID>`, and the Customer's pointer to it,
`checkout-customer:<Customer ID>`, holds its ID. Both are written in one script with a TTL that
runs to the Reservation's expiry, 2 minutes after the session's. The session's own `expiresAt`, on
Checkout's clock (the `TimeSource` port), is what decides it has expired: from then on it is no
longer `current`, and paying it is a 410. Applying or removing a Coupon rewrites the session's
string in place, keeping its TTL, and only while Redis still has it: a session replaced or dropped
meanwhile stays gone. Once Redis drops it, paying it is a 404, like a session
that never existed. Ending a session deletes both keys, unless the pointer already names a newer
session.

A session saved before sessions held their lines' Products and every Discount (Sprint 3, #49) can't
be evaluated again, so it reads as no session: the Customer starts checkout afresh, and its
Reservation lapses by itself.

A session that expires unpaid leaves its Reservation to Inventory, which stops holding the Stock
at the Reservation's own `expiresAt`, 2 minutes later.

## Outbound identity

Checkout calls each service with the identity that service expects
([ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)):

- **Cart** (read, clear): the Customer's own JWT, forwarded as is. The Cart adapter takes it from
  the current request's security context, so the use case and its ports only ever see the
  Customer ID.
- **Catalog**: no token; reads are public.
- **Inventory, Order Management, Payment, Promotions**: Checkout's own token, from the confidential `checkout`
  client (client credentials, `CHECKOUT` role). The Customer goes in the body as `customerId`, on
  `POST /reservations` and its commit and release, `POST /orders`, every
  `PATCH /orders/{id}/status` (the compensating `CANCELLED` too), and `POST /payments`.
  `POST /discounts/evaluate` names no Customer: a Coupon applies the same to everyone.

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
either, and the Order stays `PLACED`. That's a known Sprint 1 limitation, fixed by the checkout Saga in Sprint 4.

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
