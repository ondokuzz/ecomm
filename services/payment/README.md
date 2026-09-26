# Payment

Payment authorization for an Order, through a payment gateway. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Data lives in the `payment` database on the shared Postgres,
with the schema under Flyway ([`db/migration`](./src/main/resources/db/migration)).

For Sprint 1 the gateway is a mock that authorizes everything. It sits behind `PaymentGatewayPort`
([ADR 0005](../../docs/adr/0005-localization-compliance-abstracted.md)), so a real gateway replaces
`MockPaymentGatewayAdapter` without touching the use cases. Capture and refund come later, and an
event-sourced rebuild in Sprint 4 (see the [roadmap](../../docs/roadmap.md)).

## API

Every endpoint needs a token with the `CUSTOMER` role. A Payment belongs to the Customer who
authorized it (the token's `sub`), and only they can read it: another Customer's Payment is a 404,
the same as an unknown one, so its existence never leaks. Staff have no Payments: a token without
`CUSTOMER` gets 403.

| Endpoint | |
|---|---|
| `POST /payments` | Authorizes an Order's amount; 201 with the Payment, its URL in `Location` |
| `GET /payments/{id}` | The Customer's Payment; 404 for an unknown ID or another Customer's |

A request looks like `{"orderId": "order-1", "amount": {"amountMinor": 79900, "currency": "EUR"}}`,
the amount as `Money`: an integer in the currency's minor unit and an ISO 4217 code. A Payment
comes back as

```json
{"id": "…", "orderId": "order-1", "amount": {"amountMinor": 79900, "currency": "EUR"},
 "status": "AUTHORIZED", "gatewayReference": "mock-…"}
```

A missing or blank `orderId`, one past 64 characters, or an amount that isn't a positive integer
with a known currency is a 400. Every error is a problem detail.

Each request is a new authorization: posting the same Order twice records two Payments.
Idempotency comes with the Sprint 4 rebuild.

[`http/payment.http`](./http/payment.http) exercises every endpoint against the compose stack.

## Run it

```sh
docker compose up -d --build payment   # from the repo root; listens on localhost:8084
```

Or run Postgres and Keycloak in compose (`docker compose up -d keycloak`) and start the service
with `./gradlew :services:payment:bootRun` on port 8080. Set `POSTGRES_PORT` for both when
something else holds 5432.
