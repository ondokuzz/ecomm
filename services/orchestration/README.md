# Orchestration

The Temporal worker for every Saga ([ADR 0009](../../docs/adr/0009-sagas-on-temporal.md)). It owns
no domain data, only its workflows' state in Temporal, so it has no database and no outbox. Built
from [`platform/service-template`](../../platform/service-template/README.md), so its layout,
security and testing conventions apply here.

It has no public API, and the gateway doesn't route it. Over HTTP it serves only its health and its
metrics (`/actuator/health`, `/actuator/prometheus`); it publishes no host port. Checkout starts
its workflow through Temporal's client, never over HTTP to Orchestration, and Orchestration starts
and wakes the fulfillment Saga itself from the events it consumes. It publishes none.

## Workflows

| Task queue | Workflow type | |
|---|---|---|
| `checkout` | `checkout` | The checkout Saga ([below](#the-checkout-saga)), which Checkout starts when a Customer pays a Checkout Session |
| `fulfillment` | `fulfillment` | The fulfillment Saga ([below](#the-fulfillment-saga)), which Orchestration starts when an Order is paid |

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
 "tax": {"amountMinor": 0, "currency": "EUR"},
 "shippingAddress": {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5",
                     "postcode": "10115", "city": "Berlin", "country": "DE"},
 "shippingOption": {"id": "EXPRESS", "carrier": "Mock Carrier",
                    "cost": {"amountMinor": 999, "currency": "EUR"}},
 "reservationId": "…", "paymentMethod": "tok_approve", "correlationId": "…"}
```

`shippingAddress` and `shippingOption` joined the input in Sprint 5; a run started before has
neither.

It answers `{"status", "orderId", "declineReason"}`, `status` being `PAID`, `DECLINED`,
`HOLD_EXPIRED` or `FAILED`. The steps, in order:

1. **Place the Order** (`POST /orders`), with its Shipping address and option. Transient failures (a 5xx, a timeout, no answer) are
   retried with backoff, from 1 to 10 seconds, for up to a minute. A 4xx is a bug, not retried: the
   outcome is `FAILED`, with nothing to undo.
2. **Authorize the Payment** (`POST /payments`) for the total Order Management gave the Order.
   `AUTHORIZED` goes on. `DECLINED` cancels the Order: `DECLINED`, with the gateway's reason. A
   failure, the gateway's 502 included, is tried 4 times in all over about 10 seconds with the same
   key, then cancels the Order: `FAILED`. `PENDING`, a card the Customer's bank confirms later, is
   awaited: the Payment is read (`GET /payments/{id}/for-customer?customerId=`), backing off from 1
   to 5 seconds, until a Gateway webhook has settled it, for up to
   `ecomm.orchestration.settlement-deadline` (10 minutes). Approved goes on; declined cancels the Order, `DECLINED`; still pending at the
   deadline voids the Payment and cancels the Order, `FAILED`. A read that finds it pending isn't
   counted as a step failure.
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

Each attempt at a step may take 30 seconds (`CheckoutSaga.ATTEMPT`). A context that has sat idle can
take more than 10 seconds to answer its first call: Order Management took 13.5 on 2026-10-14. The
call itself gives up sooner, after `ecomm.orchestration.read-timeout` (25 seconds; 5 to connect,
`connect-timeout`), so a slow attempt fails in the worker and its retry replays with the same key,
rather than Temporal abandoning it while its call still waits.

Each command that takes one carries the `Idempotency-Key` `<workflowId>:<runId>:<step>`: `placeOrder`,
`authorizePayment`, `markOrderPaid` and `cancelOrder`. A retry of a step sends the same key, so it
replays; paying the session again after a decline is a new run, with keys of its own. Committing,
voiding, clearing and ending need none. Every call names the Customer, carries Orchestration's token,
and carries the Correlation ID of the Customer's Pay request: the workflow puts it in its MDC, and
`CorrelationIdPropagator` hands it to each activity's thread, so the calls and every log line carry
it.

## The fulfillment Saga

`FulfillmentSaga` takes one paid Order to its Customer, with the workflow ID `fulfillment:<orderId>`,
so Temporal's UI finds an Order's fulfillment by its ID.

**Starting it.** `FulfillmentListener` consumes `order-management.order` in the group
`orchestration.orders`, which read from the latest offset when it first started: Orders paid before
it existed aren't fulfilled. From then on it resumes where it left off, so an Order paid while
Orchestration was down is fulfilled once it is back. An Order event whose status is `PAID` and which
has a Shipping address starts the Saga, with the Order's ID, Customer, lines, address and Shipping
option, and the event's Correlation ID. The workflow ID's reuse policy refuses a second start, even
after the first run closed (for as long as Temporal keeps it, 7 days), so a redelivered or replayed
event starts nothing, and that is logged, not an error. An Order paid without a Shipping address,
before Sprint 5, starts nothing: it stays `PAID`, with no Shipment, as it was. An upgraded stack's
pre-Sprint 5 Orders were paid before the group existed anyway.

**Waking it.** The same listener consumes `fulfillment-shipping.shipment` in the group
`orchestration.shipments`, and signals `fulfillment:<orderId>` (`shipmentChanged`) with the
Shipment's ID, version and status. The workflow ignores a version it has seen, or an older one, so a
duplicate or a stale event changes nothing. A signal for a Saga that doesn't exist, or has ended, is
logged and skipped, not dead-lettered. Either group's dead-letter and redrive topics are
`<group>.dlt` and `<group>.redrive`, as for every group (`infra/docker/consumer-groups`).

It answers `{"status", "shipmentId", "holdReason"}`, `status` being `DELIVERED`,
`CAPTURE_REFUSED`, `DELIVERY_UNCONFIRMED` or `FAILED`. The steps, in order:

1. **Create the Shipment** (`POST /shipments` on Fulfillment & Shipping) from the Order's lines,
   address and option. A failure, such as the `503 productsNotKnownYet` while Fulfillment & Shipping
   awaits Catalog's Products, is retried with backoff up to a minute until it succeeds. A `409`
   (`shipmentExists`) is read as success: the Order's Shipment is read (`GET /shipments?orderId=`)
   and the Saga takes it up from its status (see "A Shipment that exists already" below). Any
   other 4xx ends it `FAILED`, with nothing charged.
2. **Await `PACKED`**, with no deadline: picking and packing are Staff's work. Each time
   `ecomm.orchestration.packing-check` (1 hour) passes without a `PACKED` event, the Shipment is
   read again (step `findShipment`), so a run whose event never comes, dead-lettered say, goes on
   once the read says it is packed. Each check adds a handful of events to the run's history, about
   200 a day, well under Temporal's limit for anything Staff would leave unpacked.
3. **Capture the Payment**: the Order's Payments are read (step `findPayment`, `GET
   /payments/by-order?orderId=&customerId=`, Payment's read for Orchestration), and the authorized
   one, or one captured already, is captured (step `capturePayment`, `POST /payments/{id}/capture`), each
   step retried on its own. A `402 captureDeclined` **holds the Shipment** (`POST
   /shipments/{id}/hold`) with the reason `Capture declined: <declineReason>`; a capture that still
   fails after about a minute (the gateway's 502, say), or an Order with no authorized Payment,
   holds it with `Capture failed: …`. Since the hold is final, a failed capture is first checked:
   the Payment is read again, retried until Payment answers, and when an attempt whose answer was
   lost captured it after all, the Saga goes on as captured. Either way the outcome is `CAPTURE_REFUSED`: the Order stays
   `PAID` and the Payment `AUTHORIZED`. Resolving it is out of scope.
4. **Mark the Order `FULFILLED`** (`PATCH /orders/{id}/status`).
5. **Dispatch the Shipment** (`POST /shipments/{id}/dispatch`), which books it with its Carrier.
6. **Mark the Order `SHIPPED`**.
7. **Await `DELIVERED`**, for up to `ecomm.orchestration.delivery-deadline` (30 days). Past it the
   outcome is `DELIVERY_UNCONFIRMED`, logged, and the Order stays `SHIPPED`.
8. **Mark the Order `DELIVERED`**. The outcome is `DELIVERED`.

Once the Payment is captured nothing is compensated: steps 4 to 6 and 8, like the hold, are retried
until they succeed. Each attempt has the checkout Saga's 30 seconds. Each command carries the
`Idempotency-Key` `fulfillment:<orderId>:<runId>:<step>` (`createShipment`, `capturePayment`,
`holdShipment`, `markOrderFulfilled`, `dispatchShipment`, `markOrderShipped`,
`markOrderDelivered`), and every call carries the Correlation ID of the Order event that started
the Saga. Order Management records each status change as made by `ORCHESTRATION`.

**A Shipment that exists already.** A second run of an Order's fulfillment starts when its `PAID`
event is replayed after Temporal stopped keeping the first (7 days). Its keys carry its own run ID,
so they replay nothing of the first run's, and Order Management refuses a status change the Order
has made already (`409`). So the run takes the Shipment up from the status it reads, skipping what
is done:

| Shipment found | What the run does |
| --- | --- |
| `AWAITING_PICK`, `PICKED` | every step from 2 on |
| `PACKED` | every step from 3 on, without awaiting an event (a capture made already is answered as captured) |
| `SHIPPED` | steps 7 and 8 only: the run that dispatched it had captured the Payment and marked the Order `FULFILLED` and `SHIPPED` |
| `DELIVERED` | step 8 only, where a `409` counts as done: the first run may have marked the Order `DELIVERED` already, or ended `DELIVERY_UNCONFIRMED` before the delivery |
| `ON_HOLD` | nothing: it ends `CAPTURE_REFUSED` with the Shipment's hold reason, as the first run did |

A status read this way carries no version, so it counts only when it is further along than the
events seen so far.

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

The first such change is `shipping-on-orders` (Sprint 5, #74): placing the Order sends its
Shipping. A run that placed its Order before the change replays at `DEFAULT_VERSION`, without
Shipping, as it ran; every run that reaches placement after it records version 1. A run started
before Sprint 5 has no Shipping in its input, so on either path Order Management refuses its
placement with a 400: it ends `FAILED`, with nothing to undo, and the Customer pays the session
again once it has a Shipping address and option. The change ID can go once no run from before
Sprint 5 is open.

## Metrics

`/actuator/prometheus` serves, beside Temporal's own SDK metrics (`temporal_workflow_completed_total`,
`temporal_activity_execution_failed_total`, `temporal_workflow_endtoend_latency_seconds`, …, tagged
with the task queue, workflow and activity type):

- `checkout_workflows_total{outcome}`: runs by outcome, a `PAID` run counted once its Order is
  marked paid;
- `checkout_step_failures_total{step}`: failed attempts of each step, before Temporal retries it;
- `checkout_compensations_total{kind}`: `cancelOrder` and `voidPayment`;
- `fulfillment_workflows_total{outcome}`: fulfillment runs by outcome;
- `fulfillment_step_failures_total{step}`: failed attempts of each fulfillment step;
- `fulfillment_holds_total{kind}`: Shipments held, `captureDeclined` or `captureFailed`.

A step's failure is counted under its Saga's workflow type, so an activity shared by both Sagas
would count in each. Temporal's Spring Boot starter reports them to Micrometer every 10 seconds, its
fixed interval, but only once it has counted something. So that Prometheus has each at 0 before its
first count, which `increase()` would otherwise miss, `SagaMetrics` registers every outcome, step,
compensation and kind of hold at 0 when Orchestration starts, with the tags Temporal's scopes give
them. It reads the steps from each Saga's activity interfaces, but a compensation or kind of hold
added to a Saga is added there by hand. Grafana's **Sagas** dashboard shows them, in a Checkout and
a Fulfillment row, the latter with the lag of the two consumer groups
([infra/docker/grafana](../../infra/docker/grafana)).

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

The secret has no default. Since Sprint 5 Compose mounts it as the file
`spring.security.oauth2.client.registration.orchestration.client-secret`, from the stack's generated
`orchestration-client-secret`, which realm sync also sets on the `orchestration` client (see the root
README's "Secrets"). `ORCHESTRATION_CLIENT_SECRET` is gone.

## Tests

`./gradlew :services:orchestration:check`. The application runs against Temporal's own test server,
reached over gRPC like the real one, with time skipping on, so a retry's backoff or an hour of
retries takes moments. One WireMock server stands in for Keycloak's token endpoint and the services
Orchestration calls:

- `CheckoutWorkflowTest`: the checkout Saga with its real activities and HTTP clients: the steps in
  order with their keys and the Correlation ID, a decline, a 409 on commit, a 503 on placement
  retried with the same key, a refused placement, a gateway that keeps failing, a failing
  compensation and a failing mark-paid retried, a Cart that can't be cleared, two runs for one
  session with keys of their own, the Order placed with its Shipping, a run started with Sprint 4's
  input (no Shipping) ending `FAILED` with nothing to undo, and the counters on
  `/actuator/prometheus`;
- `CheckoutMetricsTest`: every counter at 0 before any checkout, and a run counting on those same
  series;
- `FulfillmentWorkflowTest`: the fulfillment Saga from Kafka in (a Kafka container, with the
  consumers' topics and dead-letter topics) to WireMock out: a paid Order's event starting one Saga,
  the same event twice and a replay after it closed starting nothing more, an Order without an
  address or not yet paid starting nothing, the steps in order with their keys and the Correlation
  ID, a Shipment that exists already, one whose Products aren't known yet, duplicate and stale
  Shipment events ignored, a declined capture and an Order without an authorized Payment holding the
  Shipment with the Order untouched, a 503 on `FULFILLED` retried, delivery never confirmed ending
  `DELIVERY_UNCONFIRMED` at the delivery deadline (10 seconds here, so the test shows the setting is
  the one honoured), and an event for no running Saga skipped, not dead-lettered. It has a test
  server of its own with time skipping off, so the Saga waits for the test's events in real time.
  Its own, too, because a Saga terminated while a workflow task is outstanding leaves a test
  server's time skipping locked for good (its completion is refused, and its lock never released):
  on the shared server, later classes' retries would wait in real time;
- `FulfillmentMetricsTest`: every fulfillment counter at 0 before any run;
- `ServiceTokenTest`: the token's caching, renewal and retry on a 401;
- `HealthApiTest`: health is public, and there is no API;
- `ArchitectureTest`: the shared `HexagonalRules`, and workflow code reaching other contexts only
  through activities.

Every class runs in one JVM, with one Spring context alive at a time
(`spring.test.context.cache.maxSize=1`, build file), so no earlier context's worker or Kafka
consumer takes a later class's tasks or events. Each class first checks that the shared test server
still skips time, and fails at once if it doesn't, rather than hanging.
