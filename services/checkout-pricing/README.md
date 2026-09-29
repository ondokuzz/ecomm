# Checkout & Pricing

Turns a Customer's Cart into a paid Order in one call. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. It is stateless: it holds nothing but its own cached token.

## API

| Endpoint | Who | |
|---|---|---|
| `POST /checkout` | Customer | Checks out the caller's Cart; 200 with `{"orderId": "…", "status": "PAID"}` |

The steps run in order, each against the service that owns it:

1. read the Cart;
2. price every line from Catalog, ignoring any price the Cart shows;
3. place the Order in `PLACED` in Order Management;
4. decrement Stock in Inventory;
5. authorize the payment for the total (lines plus tax) in Payment;
6. set the Order to `PAID`;
7. clear the Cart;
8. return the Order's ID and Order Status.

Tax comes from the `TaxCalculator` port. For now that is `ZeroTaxCalculator`
([ADR 0005](../../docs/adr/0005-localization-compliance-abstracted.md)). An Order can't record tax
yet, so a non-zero tax would authorize a Payment larger than the Order's total. Until
[#14](https://github.com/ondokuzz/ecomm/issues/14), checkout refuses non-zero tax with a 500
before any Order exists.

Until multi-Variant Products arrive, a Variant ID is its Product's SKU, so a line is priced from
`GET /products/{variantId}` and the Variant with that ID in it.

### Errors

Every error is a problem detail.

| Status | When |
|---|---|
| 400 | The Cart is empty |
| 401 / 403 | No token / not a Customer (Staff and services have no Cart) |
| 409 | `unknownVariants`: Catalog no longer has these Variants. `outOfStock`: Inventory can't cover these. Also a Cart priced in more than one currency |
| 502 | Another service failed or answered unexpectedly |
| 503 | Keycloak couldn't issue Checkout's own token |

A 400 or a 409 from Catalog happens before any Order exists. Once the Order exists, a failing step
sets it to `CANCELLED` and leaves the Cart as it was. That's the only compensation for now: Stock
already decremented stays decremented, and an authorized Payment stays authorized. The Sagas in
Sprint 3 fix that. A Cart that can't be cleared after the Order is paid is only logged, since the
Customer keeps the paid Order either way.

## Outbound identity

Checkout calls each service with the identity that service expects
([ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)):

- **Cart** (read, clear): the Customer's own JWT, forwarded as is. The Cart adapter takes it from
  the current request's security context, so the use case and its ports only ever see the
  Customer ID.
- **Catalog**: no token; reads are public.
- **Inventory, Order Management, Payment**: Checkout's own token, from the confidential `checkout`
  client (client credentials, `CHECKOUT` role). The Customer goes in the body as `customerId`, on
  `POST /orders`, every `PATCH /orders/{id}/status` (the compensating `CANCELLED` too), and
  `POST /payments`.

The client secret comes from `CHECKOUT_CLIENT_SECRET`, which compose sets; there is no default.

Every call, to every service, also carries the checkout request's Correlation ID in
`X-Correlation-Id`, so one checkout can be followed through all their logs (see the
[service template](../../platform/service-template/README.md#correlation-ids-and-logs)).

Spring Security's OAuth2 client fetches the token and caches it in memory. It fetches a new one
before any call when the cached one has less than 60 seconds left. On a 401 from a downstream
service, the cached token is dropped and the call is retried exactly once with a fresh one. That
is safe even for a `POST`, because a resource server rejects a bad token before any handler runs.
No other status is retried.

If Keycloak is unreachable before the Order is placed, checkout fails with a 503. If it becomes
unreachable afterwards, the compensating `CANCELLED` can't be sent either, and the Order stays
`PLACED`. That's a known Sprint 1 limitation, fixed by the Saga work.

[`http/checkout-pricing.http`](./http/checkout-pricing.http) checks out the demo Customer's Cart
against the compose stack.

## Run it

```sh
docker compose up -d --build checkout-pricing   # from the repo root; listens on localhost:8086
```

Or run the rest of the stack in compose and start the service with
`CHECKOUT_CLIENT_SECRET=checkout-dev-secret ./gradlew :services:checkout-pricing:bootRun` on port
8080. It finds the other services on their compose host ports.
