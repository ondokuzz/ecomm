# Checkout & Pricing

Turns a Customer's Cart into a paid Order in two steps: a **Checkout Session** holds the Cart for
15 minutes, at the Prices of the moment it started, with every running **Campaign**'s Discount it is
due, and may take a **Coupon** on top; paying the session starts the **checkout Saga**, which
Orchestration runs on Temporal ([ADR 0009](../../docs/adr/0009-sagas-on-temporal.md)), and buys it.
Built from
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
| `POST /checkout/sessions/{id}/pay` | Pays it with `{"paymentMethod": "…"}`; 200 with `{"orderId": "…", "status": "PAID"}`, or 202 while the Saga is still going ([below](#paying-a-session)) |
| `GET /checkout/sessions/{id}/payment` | The latest attempt to pay it: `{"status", "orderId", "declineReason"}` |

One endpoint is internal. The checkout Saga ends a Customer's Checkout Session once their Order is
paid, with Orchestration's own token (`ORCHESTRATION`,
[ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)), naming
the Customer in the body. Any other token, a Customer's or Checkout's included, gets 403, and the
API gateway doesn't route it.

| Endpoint | |
|---|---|
| `POST /checkout/sessions/{id}/end` | Body `{"customerId": "…"}`: ends the session if the Customer's pointer still names it; 204 whether or not it did. A missing or blank `customerId` is a 400 |

Ending a session the Customer has since replaced, or one already gone, changes nothing, so a
repeat is harmless and the command takes no `Idempotency-Key`.

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
payment gateway, which Checkout passes on unread (the mock's test tokens are in
[Payment's README](../payment/README.md#gateways)). A missing, blank or non-string one, or one past
255 characters, is a 400. A session that has expired is a 410, and one that doesn't exist, has ended
or belongs to someone else is a 404; in all three cases nothing is started.

Otherwise Checkout starts the checkout Saga through its `CheckoutSaga` port, whose adapter is
Temporal's client. Checkout calls no other service to pay: Orchestration's workflow places the Order,
authorizes the Payment for the Order's total, commits the Reservation, marks the Order paid, clears
the Cart and ends the session, and undoes what it must when a step can't go on
([Orchestration](../orchestration/README.md#the-checkout-saga)).

- The workflow is `checkout`, on the task queue `checkout`, and its ID is the session's. While one
  runs for the session, paying again joins it rather than starting another. Once it has closed,
  paying again starts a new run, so a Customer can pay again after a decline.
- A session whose latest run paid it is never paid again: paying answers that run's 200.
- Its input is the session as it stands, with the Customer, the lines at their captured Prices,
  every Discount, the tax, the Reservation's ID and the Payment method, and the Correlation ID of
  the Pay request, which every call the Saga makes carries. Its memo names the Customer, so that
  only they read the attempt.
- **The Payment method token travels in the workflow's input, and so in its history**, which
  Temporal keeps for 7 days after the run closes. It is the gateway's opaque token for a card, never
  a card number, so it may.

Checkout then waits up to 10 seconds (`ecomm.checkout.saga.wait`) for how the run ends:

| Outcome | Answer |
|---|---|
| `PAID` | 200 `{"orderId", "status": "PAID"}` |
| `DECLINED` | 402 with `declineReason`, such as `insufficient_funds`. The Order is cancelled; the session and its Reservation stay, so it can be paid again |
| `HOLD_EXPIRED` | 410 with `reason` `holdExpired`: the Reservation no longer held the Stock, so the Payment was voided and the Order cancelled. Nothing was charged |
| `FAILED` | 502, with the Correlation ID as every problem detail has it. Whatever the run did is undone |
| still running | 202 `{"status": "PROCESSING", "orderId": null, "declineReason": null}`, with `Location: /checkout/sessions/{id}/payment` |

A run reports `PAID` as soon as its Order is marked paid, though it goes on to clear the Cart and
end the session: it keeps that outcome in its memo meanwhile, and Checkout reads it from there.

`GET /checkout/sessions/{id}/payment` answers the latest run's outcome in the same shape, `status`
being `PROCESSING`, `PAID`, `DECLINED`, `HOLD_EXPIRED` or `FAILED`. It reads the workflow, not the
session, so it still answers once the session has ended, for as long as Temporal keeps the run. A
session never paid, or another Customer's, is a 404.

Temporal being unreachable is a 503 with `reason` `checkoutUnavailable`, and nothing is started.
Temporal's client gives up after 5 seconds (`ecomm.checkout.saga.unavailable-after`) rather than its
default minute, so the Customer isn't kept waiting.

Tax comes from the `TaxCalculator` port, on the subtotal less every Discount. For now that is
`ZeroTaxCalculator` ([ADR 0005](../../docs/adr/0005-localization-compliance-abstracted.md)). It is
worked out when the session starts and again whenever its Coupon changes, and goes to the Saga with
the session; the Order records it. The Payment is authorized for the total Order Management gives
the Order, never one worked out from the session, so the two always match.

### Errors

Every error is a problem detail.

| Status | When |
|---|---|
| 400 | Starting: the Cart is empty. Paying: no usable `paymentMethod`. Applying a Coupon: no usable `code` |
| 401 / 403 | No token / not a Customer (Staff and services have no Cart) |
| 404 | No live session (`current`), paying or changing the Coupon of one the Customer doesn't have, or reading the payment of one never paid or not theirs |
| 409 | Starting: `unknownVariants`: Catalog no longer has these Variants. `outOfStock`: Inventory can't hold these. Also a Cart priced in more than one currency |
| 402 | Paying: the gateway declined the payment; `declineReason` gives its reason |
| 422 | Applying a Coupon that doesn't apply; `reason` says why, as Promotions does |
| 410 | Paying, or changing the Coupon of, a session that has expired. Paying, with `reason` `holdExpired`: the hold ran out during the Saga |
| 502 | Starting or changing a Coupon: another service failed or answered unexpectedly, or Promotions answered Discounts that can't be right. Paying: the Saga failed |
| 503 | Keycloak couldn't issue Checkout's own token, or, paying, Temporal can't be reached (`checkoutUnavailable`) |

Starting a session changes nothing when it fails, except that a replaced session is gone and its
Stock released. A payment that ends without paying leaves the Cart and the session as they were, so
the Customer can pay again, with another Payment method, until it expires; each attempt places a new
Order.

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

- **Cart** (read): the Customer's own JWT, forwarded as is. The Cart adapter takes it from
  the current request's security context, so the use case and its ports only ever see the
  Customer ID.
- **Catalog**: no token; reads are public.
- **Inventory, Promotions**: Checkout's own token, from the confidential `checkout` client (client
  credentials, `CHECKOUT` role). The Customer goes in the body as `customerId`, on
  `POST /reservations` and its release. `POST /discounts/evaluate` names no Customer: a Coupon
  applies the same to everyone.

Order Management, Payment, Inventory's commit, Cart's clear and Checkout's own end-session command
are the checkout Saga's, called with Orchestration's token.

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

If Keycloak is unreachable when a session starts, or a Coupon changes, the step fails with a 503.

[`http/checkout-pricing.http`](./http/checkout-pricing.http) checks out the demo Customer's Cart
against the compose stack.

## Run it

```sh
docker compose up -d --build checkout-pricing   # from the repo root; listens on localhost:8086
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/checkout-pricing/`.

Or run the rest of the stack in compose, Redis and Temporal included, and start the service with
`CHECKOUT_CLIENT_SECRET=checkout-dev-secret ./gradlew :services:checkout-pricing:bootRun` on port
8080. It finds the other services on their compose host ports, and Temporal on `localhost:7233`.

## Tests

`./gradlew :services:checkout-pricing:check`. The application runs against Redis in Testcontainers,
with one WireMock server for the services it calls and Keycloak's token endpoint, and a fake
`CheckoutSaga` port that ends each attempt as a test says, or keeps it running. `PaySessionApiTest`
covers each outcome's status, 202 then the payment endpoint, a second Pay joining the first, an
unreachable Temporal, and an expired or unknown session starting nothing; `PayRequestApiTest`, a
missing or invalid `paymentMethod` starting nothing. `TemporalCheckoutSagaTest` runs the Temporal
adapter itself against Temporal's test server, with a stand-in for Orchestration's workflow: start,
join while running, start again after it closes, read the outcome.
