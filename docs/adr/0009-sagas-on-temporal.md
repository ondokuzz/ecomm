---
status: accepted
supersedes: ADR-0007
---

# Sagas run on Temporal, not Axon

The checkout, order-fulfillment and returns-approval Sagas, and their long waits (such as a Warranty Window on an RMA), run as Temporal workflows, replacing Axon's `@Saga` and the Quartz-backed `DeadlineManager` chosen in [ADR 0007](./0007-sagas-on-axon-and-quartz.md). There are two reasons. Axon Framework 5 dropped its Saga support: no Saga SPI or SagaStore, only an `axon-legacy` bridge. Staying on Axon Sagas would therefore pin the platform to Axon 4 or to a legacy module. And the strengths ADR 0007 set aside, visibility into in-flight workflows and safe versioning when workflow code changes mid-flight, matter more for multi-week processes like returns than the cost of one more runtime.

Every Saga runs in one `orchestration` service, the Temporal worker for all of them. It owns no domain data, only the state of its workflows. The workflows are:

- **checkout**, from Sprint 4: place the Order, authorize the Payment, commit the Reservation, mark the Order paid, clear the Cart and end the Checkout Session;
- **fulfillment**, from Sprint 5: pick, pack and ship;
- **returns**, from Sprint 5: an RMA from request to resolution, with the Warranty Window as a durable timer.

A Saga step is an activity that calls the owning context's command API directly over HTTP, with Orchestration's own client-credentials identity and an `Idempotency-Key` header of `<workflowId>:<runId>:<activity>`. The context records the key in the same transaction as the change, so a repeated call returns the first result instead of acting twice; a command that is naturally idempotent, such as committing a Reservation that is already committed, needs no key. Temporal retries a step that fails or times out. A business refusal, such as a declined Payment, is not retried: the workflow compensates instead.

Another service starts a workflow through the Temporal client, not over HTTP to Orchestration: Checkout starts the checkout workflow when a Customer pays a Checkout Session, with the session's ID as the workflow's, and waits about 10 seconds for its result before answering `202`.

## Consequences

- Temporal is a runtime to operate. It runs in Compose from Sprint 4, when the checkout Saga arrives, persisting to the shared Postgres in its own `temporal` and `temporal_visibility` databases, with its web UI for inspecting workflows.
- Checkout's remaining gap closes: a Payment authorized before the Reservation fails to commit is voided, and the Order cancelled.
- Every command a Saga calls takes an idempotency key, and only Orchestration's identity may call it ([Identity & Access ADR 0002](../../services/identity-access/docs/adr/0002-service-identity-by-client-credentials.md)).
- Temporal orchestrates the steps; Kafka is not a second channel between them. Contexts still publish integration events to Kafka for everyone else ([ADR 0006](./0006-kafka-as-single-event-backbone.md)).
- A workflow's history is a record of the process, kept for Temporal's retention period. It is not the record of an Order, Payment or RMA, which the owning context keeps ([ADR 0002](./0002-ledgers-and-outboxes-not-event-sourcing.md)).

## Revised 2026-10-03

This ADR said that Saga steps talk to other contexts through commands and events on Kafka, and that Axon would keep its role for event-sourced aggregates and the Postgres event store. Axon is gone, since no context is event-sourced ([ADR 0002](./0002-ledgers-and-outboxes-not-event-sourcing.md)). Over Kafka, every step would need a command topic, a consumer in the target context and a reply listener mapping replies back to workflows, and a stuck step could hide in consumer lag that Temporal can't see. Calling command APIs directly leaves every retry, timeout and compensation in the workflow, where Temporal's UI shows it. It also settles what this ADR left open: one service hosts every Saga, and the first Saga is checkout's, in Sprint 4 alongside Payment's ledger, instead of fulfillment's in Sprint 5.

## Revised 2026-10-07

As built (#55): Temporal's server 1.32 runs in Compose with its schema set up or migrated by an init step on every start, in the `default` namespace with a 7-day retention, so a closed workflow's history is kept for a week. Its UI is on port 8233. Orchestration is the worker, on the Temporal Java SDK 1.40 and its Spring Boot starter, which a spike proved under Spring Boot 4.1 and Jackson 3.

## Revised 2026-10-08

Idempotency keys (#56): Orchestration's key is `<workflowId>:<runId>:<activity>`, where this ADR said `<workflowId>:<activity>`. The workflow ID is the Checkout Session's ID, so a Customer who pays a session again after a decline starts a new run of the same workflow ID. Without the run ID, that run's steps would send the first run's keys and collide with them: a step the first run completed would replay its response instead of acting for the new attempt. The run ID stays the same across a step's retries within one run, so a retry still replays. Keys are scoped to the calling client and kept for 7 days, as long as Temporal keeps a closed workflow's history.

A context records the key without its use case seeing a port for it. `service-commons` opens the transaction around a command declared `@IdempotentCommand`, claims the key in it, and records the response before committing. The use case's own transaction joins it. That transaction carries both the key and the change only if the use case's transaction joins it on the same Postgres transaction manager. A use case that starts a new transaction, or writes elsewhere, falls outside it ([service template](../../platform/service-template/README.md#idempotent-commands)).

## Revised 2026-10-09

The checkout Saga as built (#60). Checkout starts it through a `CheckoutSaga` port with Temporal's client: the workflow type `checkout` on the `checkout` task queue, its ID the Checkout Session's, so a second Pay while it runs joins it and a Pay after it closed starts a new run, unless that run paid the session, whose outcome is then the answer. Checkout waits about 10 seconds, then answers `202` and the Storefront polls Checkout's payment endpoint, which reads the workflow. The workflow reports `PAID` as soon as the Order is marked paid, by keeping the outcome in its memo while it clears the Cart and ends the session, which it retries for up to an hour. Placing the Order and committing its Stock are retried for about a minute, authorizing 4 times over about 10 seconds, and marking the Order paid and every compensation until they succeed. A 4xx on placing, authorizing or committing is not retried; marking paid and the compensations retry even a refusal. The Correlation ID of the Pay request travels in the workflow's input and on to every activity's calls. A `PENDING` authorization is voided until the Saga can await settlement (#61). `CHECKOUT` lost its access to the commands the Saga took over: placing and changing Orders, authorizing and voiding Payments, and committing Reservations.
