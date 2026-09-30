# Payment

Payment authorization for an Order, through a payment gateway. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Data lives in the `payment` database on the shared Postgres,
with the schema under Flyway ([`db/migration`](./src/main/resources/db/migration)).

The gateway sits behind `PaymentGatewayPort`
([ADR 0005](../../docs/adr/0005-localization-compliance-abstracted.md)), so a real gateway joins the
mock without touching the use cases. Capture and refund come later, and an event-sourced rebuild in
Sprint 4 (see the [roadmap](../../docs/roadmap.md)).

## Gateways

`payment.gateway` (or `PAYMENT_GATEWAY`) picks the adapter, in `PaymentGatewayConfiguration`. It
defaults to `mock`, the only one so far; any other value fails at startup, so the service never
runs without a gateway.

The mock, `MockPaymentGatewayAdapter`, reads the request's `paymentMethod` as a test token:

| `paymentMethod` | Outcome |
|---|---|
| `tok_approve` | `AUTHORIZED` |
| `tok_decline` | `DECLINED`, `declineReason` `card_declined` |
| `tok_insufficient_funds` | `DECLINED`, `declineReason` `insufficient_funds` |
| `tok_gateway_error` | the gateway fails to answer: a 502, and no Payment is recorded |
| anything else | `DECLINED`, `declineReason` `unknown_payment_method` |

Each answer carries a fresh `mock-…` reference, a decline's too.

## API

Checkout authorizes payments with its own token, with the `CHECKOUT` role
([ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)), and names
the Customer the Payment is for. That Customer reads it back with their own token (`CUSTOMER`),
whose `sub` must match: another Customer's Payment is a 404, the same as an unknown one, so its
existence never leaks. Any other token gets 403, and no token gets 401. Staff have no Payments.

| Endpoint | Called by | |
|---|---|---|
| `POST /payments` | Checkout | Authorizes an Order's amount; 201 with the Payment, authorized or declined, its URL in `Location` |
| `GET /payments/{id}` | Customer | The Customer's Payment; 404 for an unknown ID or another Customer's |

A request looks like

```json
{"customerId": "…", "orderId": "order-1", "paymentMethod": "tok_approve",
 "amount": {"amountMinor": 79900, "currency": "EUR"}}
```

the `paymentMethod` an opaque token from the gateway for the Customer's card, and the amount as
`Money`: an integer in the currency's minor unit and an ISO 4217 code. A Payment comes back as

```json
{"id": "…", "orderId": "order-1", "amount": {"amountMinor": 79900, "currency": "EUR"},
 "status": "AUTHORIZED", "declineReason": null, "gatewayReference": "mock-…"}
```

A declined payment is recorded too, and is still a 201: its `status` is `DECLINED` and its
`declineReason` the gateway's reason, such as `insufficient_funds`. Its owner reads it back like
any other. A gateway that fails to answer is a 502, and nothing is recorded.

A missing or blank `customerId` or one past 255 characters, a missing or blank `orderId` or one past
64 characters, a missing or blank `paymentMethod`, one that isn't a string or one past 255
characters, or an amount that isn't a positive integer with a known currency is a 400. Every error
is a problem detail.

Each request is a new authorization: posting the same Order twice records two Payments.
Idempotency comes with the Sprint 4 rebuild.

[`http/payment.http`](./http/payment.http) exercises every endpoint against the compose stack.

## Run it

```sh
docker compose up -d --build payment   # from the repo root; listens on localhost:8084
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Payment has no route through the gateway: only Checkout calls it.

Or run Postgres and Keycloak in compose (`docker compose up -d keycloak`) and start the service
with `./gradlew :services:payment:bootRun` on port 8080. Set `POSTGRES_PORT` for both when
something else holds 5432.
