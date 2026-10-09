# Orchestration

The Temporal worker for every Saga ([ADR 0009](../../docs/adr/0009-sagas-on-temporal.md)). It owns
no domain data, only its workflows' state in Temporal, so it has no database and no outbox. Built
from [`platform/service-template`](../../platform/service-template/README.md), so its layout,
security and testing conventions apply here.

It has no public API, and the gateway doesn't route it. Over HTTP it serves only its health and its
metrics (`/actuator/health`, `/actuator/prometheus`); it publishes no host port. Other services
start its workflows through Temporal's client, never over HTTP to Orchestration.

## Workflows

| Task queue | Workflow type | |
|---|---|---|
| `checkout` | `checkout` | The checkout Saga ([below](#the-checkout-saga)), which Checkout starts when a Customer pays a Checkout Session |

The workers, their workflow classes and their activity beans are declared in `application.yml`
under `spring.temporal.workers`, through Temporal's Spring Boot starter. Workflow interfaces are
inbound ports (`application.port.in`) and their implementations sit in `application`. Activity
interfaces are outbound ports (`application.port.out`), implemented by the HTTP clients in
`adapter.out.http`, so workflow code reaches other contexts only through activities.
`ArchitectureTest` holds workflow code to that: it may use only its own ports, Temporal's workflow
API with its activity options, failures and metrics, SLF4J and plain Java.

Temporal's Java SDK 1.40 and its Spring Boot starter run under Spring Boot 4.1: the starter's
auto-configuration loads, and workflow payloads go through the SDK's own Jackson 2 (2.21, managed
by Boot) beside Boot's Jackson 3. A record holding an `Instant` round-trips. The starter's
dependency on Temporal's test server is left out of the image; only the tests use it.

## The checkout Saga

`CheckoutSaga` runs one Checkout Session's payment, started with the workflow ID of the session's
ID. Its input is the session as it stood when the Customer paid, as JSON:

```json
{"checkoutSessionId": "…", "customerId": "…",
 "lines": [{"variantId": "PHN-PIXEL-9", "quantity": 2,
            "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}],
 "discounts": [{"source": "COUPON", "couponCode": "WELCOME10", "campaignId": null,
                "campaignName": null, "amount": {"amountMinor": 15980, "currency": "EUR"}}],
 "tax": {"amountMinor": 0, "currency": "EUR"}, "reservationId": "…",
 "paymentMethod": "tok_approve", "correlationId": "…"}
```

It answers `{"status", "orderId", "declineReason"}`, `status` being `PAID`, `DECLINED`,
`HOLD_EXPIRED` or `FAILED`. The steps, in order:

1. **Place the Order** (`POST /orders`). Transient failures (a 5xx, a timeout, no answer) are
   retried with backoff, from 1 to 10 seconds, for up to a minute. A 4xx is a bug, not retried: the
   outcome is `FAILED`, with nothing to undo.
2. **Authorize the Payment** (`POST /payments`) for the total Order Management gave the Order.
   `AUTHORIZED` goes on. `DECLINED` cancels the Order: `DECLINED`, with the gateway's reason. A
   failure, the gateway's 502 included, is tried 4 times in all over about 10 seconds with the same
   key, then cancels the Order: `FAILED`. A `PENDING` authorization can't be awaited yet (#61): it
   is voided and the Order cancelled, `FAILED`.
3. **Commit the Reservation** (`POST /reservations/{id}/commit`), retried as step 1. A 409
   (`reservationExpired` or `reservationReleased`) voids the Payment and cancels the Order:
   `HOLD_EXPIRED`, nothing charged. Any other failure that outlasts its retries does the same, as
   `FAILED`.
4. **Mark the Order paid** (`PATCH /orders/{id}/status`), retried until it succeeds: the Stock is
   committed, so there is no going back. The outcome is `PAID` from here, kept in the run's memo as
   `outcome`, where Checkout reads it while the run goes on.
5. **Clear the Cart** (`POST /carts/clear`).
6. **End the Checkout Session** (`POST /checkout/sessions/{id}/end`).

Steps 5 and 6 are retried for up to an hour, then logged and given up: the Customer keeps the paid
Order either way. The compensations, cancelling the Order and voiding the Payment, are retried until
they succeed, with backoff up to a minute.

Each command that takes one carries the `Idempotency-Key` `<workflowId>:<runId>:<step>`: `placeOrder`,
`authorizePayment`, `markOrderPaid` and `cancelOrder`. A retry of a step sends the same key, so it
replays; paying the session again after a decline is a new run, with keys of its own. Committing,
voiding, clearing and ending need none. Every call names the Customer, carries Orchestration's token,
and carries the Correlation ID of the Customer's Pay request: the workflow puts it in its MDC, and
`CorrelationIdPropagator` hands it to each activity's thread, so the calls and every log line carry
it.

### Changing the workflow

Workflow code must replay the same way every time: no I/O, clock, randomness or threads of its own,
only activities, `Workflow.*` and plain logic. A run started by one version of the code is finished
by whichever version is running when it next wakes up, so from the first change after Sprint 4 on,
a change that would alter what an in-flight run does next goes behind Temporal's versioning API:

```java
var version = Workflow.getVersion("void-before-cancel", Workflow.DEFAULT_VERSION, 1);
if (version == Workflow.DEFAULT_VERSION) {
  // what runs started before the change do
} else {
  // what new runs do
}
```

The change ID names the change, and stays in the code until no run started before it is still open
(at most 7 days after the last such run closed). Activity inputs and outputs change the same way: a
new field is optional, and a removed one is still read until old runs are gone.

## Metrics

`/actuator/prometheus` serves, beside Temporal's own SDK metrics (`temporal_workflow_completed_total`,
`temporal_activity_execution_failed_total`, `temporal_workflow_endtoend_latency_seconds`, …, tagged
with the task queue, workflow and activity type):

- `checkout_workflows_total{outcome}`: runs by outcome, a `PAID` run counted once its Order is
  marked paid;
- `checkout_step_failures_total{step}`: failed attempts of each step, before Temporal retries it;
- `checkout_compensations_total{kind}`: `cancelOrder` and `voidPayment`.

Temporal's Spring Boot starter reports them to Micrometer every 10 seconds, its fixed interval. Grafana's **Checkout Saga** dashboard shows
them ([infra/docker/grafana](../../infra/docker/grafana)).

## Temporal

Orchestration connects to `temporal:7233` in Compose (`localhost:7233` run from the host), in the
namespace `default`. Temporal stores its state in the shared Postgres, in its own `temporal` and
`temporal_visibility` databases, and keeps a closed workflow's history for 7 days. Its web UI is on
http://localhost:8233, where every workflow, its history and its retries can be followed.

Compose brings it up in three steps:

- `temporal-schema` sets up Temporal's schema in both databases, or migrates it to the server's
  version (`infra/docker/temporal-schema.sh`);
- `temporal` starts the server;
- `temporal-namespace` creates `default` with its 7-day retention, or sets that retention on it
  (`infra/docker/temporal-namespace.sh`).

All three run on every `make up`, and do nothing to a schema or namespace that is already current.

## Outbound identity

The Saga's steps call other contexts with Orchestration's own token, from the confidential
`orchestration` client in the `ecomm` realm, whose service account holds `ORCHESTRATION` ([Identity
& Access ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)).
Every client built by `ServiceRestClients` carries it, as Checkout's internal clients carry
Checkout's:

- the token is fetched with the client credentials and cached in memory;
- it is replaced once it has less than a minute left;
- a 401 drops it, and the call is retried once with a fresh one. No other status is retried here;
  retrying a failed step is Temporal's job.

The secret comes from `ORCHESTRATION_CLIENT_SECRET`, which has no default; Compose sets the dev-only
`orchestration-dev-secret`, matching the realm file.

## Tests

`./gradlew :services:orchestration:check`. The application runs against Temporal's own test server,
reached over gRPC like the real one, with time skipping on, so a retry's backoff or an hour of
retries takes moments. One WireMock server stands in for Keycloak's token endpoint and the services
Orchestration calls:

- `CheckoutWorkflowTest`: the checkout Saga with its real activities and HTTP clients: the steps in
  order with their keys and the Correlation ID, a decline, a 409 on commit, a 503 on placement
  retried with the same key, a refused placement, a gateway that keeps failing, a failing
  compensation and a failing mark-paid retried, a Cart that can't be cleared, two runs for one
  session with keys of their own, and the counters on `/actuator/prometheus`;
- `ServiceTokenTest`: the token's caching, renewal and retry on a 401;
- `HealthApiTest`: health is public, and there is no API;
- `ArchitectureTest`: the shared `HexagonalRules`, and workflow code reaching other contexts only
  through activities.
