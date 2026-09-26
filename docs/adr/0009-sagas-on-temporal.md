---
status: accepted
supersedes: ADR-0007
---

# Sagas run on Temporal, not Axon

The order-fulfillment and returns-approval Sagas, and their long waits (such as a Warranty Window on an RMA), run as Temporal workflows, replacing Axon's `@Saga` and the Quartz-backed `DeadlineManager` chosen in [ADR 0007](./0007-sagas-on-axon-and-quartz.md). There are two reasons. Axon Framework 5 dropped its Saga support: no Saga SPI or SagaStore, only an `axon-legacy` bridge. Staying on Axon Sagas would therefore pin the platform to Axon 4 or to a legacy module. And the strengths ADR 0007 set aside, visibility into in-flight workflows and safe versioning when workflow code changes mid-flight, matter more for multi-week processes like returns than the cost of one more runtime.

## Consequences

- Temporal becomes a new runtime to operate. It runs in Compose from the sprint that introduces the first Saga, persisting to the shared Postgres in its own database, with its web UI for inspecting workflows.
- Axon Framework keeps its role for event-sourced aggregates and the Postgres event store ([ADR 0002](./0002-cqrs-event-sourcing-scoped-to-four-contexts.md), [ADR 0008](./0008-event-store-on-postgres-not-axon-server.md)). Since Sagas no longer depend on it, choosing between Axon 4.13 and 5.x is decided on event-sourcing grounds alone when Sprint 3 starts.
- Saga steps still talk to other contexts through their commands and events on Kafka ([ADR 0006](./0006-kafka-as-single-event-backbone.md)). Temporal orchestrates the steps; it is not a second message bus between contexts.
