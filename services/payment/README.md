# Payment

Payment authorization for an Order, through a payment gateway, kept as a ledger of Payment
transactions ([ADR 0002](../../docs/adr/0002-ledgers-and-outboxes-not-event-sourcing.md)). Built
from [`platform/service-template`](../../platform/service-template/README.md), so its layout,
security and testing conventions apply here. Data lives in the `payment` database on the shared
Postgres, with the schema under Flyway ([`db/migration`](./src/main/resources/db/migration)).

The gateway sits behind `PaymentGatewayPort`
([ADR 0005](../../docs/adr/0005-localization-compliance-abstracted.md)), so a real gateway joins the
mock without touching the use cases. Capture and refund come with fulfillment and returns.

## The ledger

A Payment is an Order's amount for one Customer. Each interaction with the gateway is one Payment
transaction, recorded for good: Payment never changes or deletes one, and the database refuses to.
A transaction is an `AUTHORIZATION` or a `VOID`, with its amount, its outcome (`APPROVED`,
`DECLINED` or `PENDING`), the gateway's reference and decline reason, the gateway's event ID when a
webhook recorded it, when it happened, and whether it was backfilled.

A Payment's status is worked out from its transactions, oldest first (`PaymentStatus`):

| Transactions | Status |
|---|---|
| an approved authorization | `AUTHORIZED` |
| a declined authorization | `DECLINED`, final, with the gateway's reason |
| a pending authorization | `PENDING`, until the gateway settles it with an approved or declined one |
| an authorized or pending one, then a void | `VOIDED`, final |

Nothing else is a Payment: a pure domain test, `PaymentStatusTest`, tries every sequence of up to
three transactions. The status is kept on the Payment's row beside its transactions, written in
the same database transaction as each one from the status they give, so a read stays one query.
The mock gateway never answers `PENDING` yet: bank-confirmed payments come with #61.

## Gateways

`payment.gateway` (or `PAYMENT_GATEWAY`) picks the adapter, in `PaymentGatewayConfiguration`. It
defaults to `mock`, the only one so far; any other value fails at startup, so the service never
runs without a gateway.

Every call to the gateway carries an idempotency key, so a retried call never authorizes or voids
twice: an authorization passes on the caller's `Idempotency-Key`, and a void uses `void:<paymentId>`,
since a Payment is voided at most once.

The mock, `MockPaymentGatewayAdapter`, reads the request's `paymentMethod` as a test token:

| `paymentMethod` | Outcome |
|---|---|
| `tok_approve` | `AUTHORIZED` |
| `tok_decline` | `DECLINED`, `declineReason` `card_declined` |
| `tok_insufficient_funds` | `DECLINED`, `declineReason` `insufficient_funds` |
| `tok_gateway_error` | the gateway fails to answer: a 502, and no Payment is recorded |
| anything else | `DECLINED`, `declineReason` `unknown_payment_method` |

Each answer carries a fresh `mock-…` reference, a decline's too. A key the mock has seen gets its
first answer and reference back, for its 10,000 most recent keys, until the service restarts. A
failure to answer isn't remembered, so a retry can succeed. Every void succeeds, with a
`mock-void-…` reference.

## API

The checkout Saga authorizes, voids and reads Payments with Orchestration's own token, with the
`ORCHESTRATION` role
([ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)), and names
the Customer the Payment is for: in the body of a command, and as `customerId` when it reads one,
which finds nothing for another Customer's Payment. Checkout keeps authorizing and voiding with its
own token (`CHECKOUT`) until it moves onto the Saga. That Customer reads it back with their own token (`CUSTOMER`),
whose `sub` must match: another Customer's Payment is a 404, the same as an unknown one, so its
existence never leaks. Staff read every Payment for an Order with a `STAFF` token. Any other token
gets 403, and no token gets 401.

| Endpoint | Called by | |
|---|---|---|
| `POST /payments` | Orchestration, Checkout | Authorizes an Order's amount; 201 with the Payment, authorized or declined, its URL in `Location`. Requires an `Idempotency-Key` |
| `POST /payments/{id}/void` | Orchestration, Checkout | Releases an authorized Payment; 200 with the voided Payment |
| `GET /payments/{id}` | Customer | The Customer's Payment; 404 for an unknown ID or another Customer's |
| `GET /payments/{id}?customerId=` | Orchestration | The named Customer's Payment, as the Saga awaits its settlement; 404 as above |
| `GET /payments?orderId=` | Customer | The Customer's Payments for one of their Orders, newest first; an empty list for anyone else's Order |
| `GET /staff/payments?orderId=` | Staff | Every Payment for an Order, newest first |

The API gateway routes the reads, as `/api/payment/…`, and hides the two commands.

An authorization looks like

```json
{"customerId": "…", "orderId": "order-1", "paymentMethod": "tok_approve",
 "amount": {"amountMinor": 79900, "currency": "EUR"}}
```

with an `Idempotency-Key` header, the `paymentMethod` an opaque token from the gateway for the
Customer's card, and the amount as `Money`: an integer in the currency's minor unit and an ISO 4217
code. A Payment comes back with its transactions, oldest first:

```json
{"id": "…", "orderId": "order-1", "amount": {"amountMinor": 79900, "currency": "EUR"},
 "status": "AUTHORIZED", "declineReason": null, "gatewayReference": "mock-…",
 "transactions": [
   {"kind": "AUTHORIZATION", "amount": {"amountMinor": 79900, "currency": "EUR"},
    "outcome": "APPROVED", "gatewayReference": "mock-…", "declineReason": null,
    "gatewayEventId": null, "at": "2026-10-08T10:15:00.654321Z", "backfilled": false}]}
```

`gatewayReference` is the authorization's. A declined payment is recorded too, and is still a 201:
its `status` is `DECLINED` and its `declineReason` the gateway's reason, such as
`insufficient_funds`. A gateway that fails to answer is a 502, and nothing is recorded, so the
same key can be sent again.

The key works as every `@IdempotentCommand`'s does
([service template](../../platform/service-template/README.md#idempotent-commands)): a repeat with
the same key and body gets the first response back, with `Idempotent-Replayed: true`, and nothing
more is authorized; the same key with another body is a 422; no key is a 400 with
`idempotencyKeyRequired`. Orchestration is to send `<workflowId>:<runId>:<activity>`, and Checkout
sends `checkout:<orderId>:authorize`.

A void names the Customer, `{"customerId": "…"}`, and needs no key: voiding a `VOIDED` Payment
changes nothing and answers the same 200. Voiding a `DECLINED` Payment is a 409 with `reason`
`paymentNotVoidable`. A gateway that fails to answer is a 502, and the Payment stays authorized.

A missing or blank `customerId` or one past 255 characters, a missing or blank `orderId` or one past
64 characters, a missing or blank `paymentMethod`, one that isn't a string or one past 255
characters, or an amount that isn't a positive integer with a known currency is a 400, as is a read
of an Order's Payments without `orderId`. Every error is a problem detail.

[`http/payment.http`](./http/payment.http) exercises every endpoint against the compose stack.

### Breaking changes in Sprint 4

- `POST /payments` requires an `Idempotency-Key`. Posting the same Order twice with different keys
  still records two Payments.
- A Payment's response gains `transactions`, and its `status` may be `PENDING` or `VOIDED`.
- The gateway now routes the Customer's `GET /payments/{id}`.

## Events

Each change publishes one `payment.payment` event through the outbox, in the transaction that made
it, keyed by the Payment's ID. Its `change` is the status the transaction left the Payment in
(`AUTHORIZED`, `DECLINED`, `PENDING` or `VOIDED`), or `BACKFILLED`; its `version` is the number of
transactions; and its `payment` is the snapshot, with the Order, the Customer, the amount, the
status, the decline reason and every transaction. A request that changes nothing, such as a
replayed authorization or a second void, publishes nothing. Its schema,
[`payment.payment.json`](../../platform/event-schemas/schemas/payment.payment.json), is the
definition. Nothing consumes it yet.

```sh
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:19092 \
  --topic payment.payment --from-beginning --property print.key=true --property print.headers=true
```

## Upgrading from Sprint 3

A Sprint 3 Payment was one row, overwritten in place, with no time. The `V6` migration turns each
into a Payment with one backfilled `AUTHORIZATION` transaction: `APPROVED` for an authorized one,
`DECLINED` with its reason for a declined one, timed at the migration. Backfilled Payments for one
Order therefore share a time, and come back in no particular order among themselves. Once the service is up, it
publishes each of them once with `change: BACKFILLED`, 100 per database transaction, deleting each
from `payment_awaiting_backfill_event` as it goes, so starting again, or on another instance,
publishes nothing more.

## Run it

```sh
docker compose up -d --build payment   # from the repo root; listens on localhost:8084
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only.

Or run Postgres, Kafka, Apicurio and Keycloak in compose (`docker compose up -d keycloak kafka
apicurio`) and start the service with `./gradlew :services:payment:bootRun` on port 8080. Set
`POSTGRES_PORT` for both when something else holds 5432.
