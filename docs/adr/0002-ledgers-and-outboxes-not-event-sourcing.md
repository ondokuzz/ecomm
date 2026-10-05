# Ledgers and outboxes, not event sourcing

No context is event-sourced. A context keeps its current state in ordinary tables and, where a requirement calls for history, records it in an append-only table written in the same transaction as the state change. Every context that publishes integration events writes them to a transactional outbox in that same transaction, and a relay carries them to Kafka ([ADR 0006](./0006-kafka-as-single-event-backbone.md)).

The test for keeping a full history of facts is a ledger's: is the state *defined* as the sum of its facts, and must those facts be reconciled against an outside party? Where both hold, the facts are the source of truth and the current state is derived from them, or kept beside them in the same transaction. That is most of what event sourcing would give, without an event-sourcing framework, an event store or replayed projections.

| Context | Model | Why |
|---|---|---|
| Order Management | CRUD, with an append-only Order Status history | An Order's lines, Discount and tax never change once placed; only its Status moves, so its history is a list of status transitions. |
| Payment | Ledger: an append-only Payment transaction per gateway interaction, with the gateway's reference and answer | A Payment is the sum of its authorizations, captures, voids and refunds, reconciled against the gateway's records. |
| Inventory | Ledger: an append-only Stock movement per change to On-hand or to what Reservations hold, beside the Stock counter | On-hand must always be recomputable from its movements, while availability stays one conditional update. A Reservation holds several Variants all or nothing, which is one Postgres transaction here and a cross-aggregate problem under event sourcing. |
| Returns & Warranty | CRUD, with an append-only status history recording who decided and why | A warranty dispute needs the decision history (approvals, inspection results, captured IMEI or serial), not a replay of the RMA. Its money and Stock effects land in Payment's and Inventory's ledgers. |
| Everything else | Plain CRUD | No history requirement. |

Integration events are state-carrying: each one carries the state its consumers need, and the version of the aggregate it describes, which goes up with every change. Delivery is at least once and not strictly in order, so a consumer keeps the newest version it has seen and ignores any event whose version is not newer. The Postgres-backed services publish through Spring Modulith's event publication registry, which is their outbox, and its Kafka externalization; Catalog, on Couchbase, has its own outbox behind the same port. Each event's JSON Schema lives in the repo and is registered in an Apicurio registry, stored in Postgres, with backward compatibility enforced; producers validate against it, and consumers read tolerantly.

## Considered Options

- **CQRS and event sourcing on Axon Framework for Order Management, Payment, Inventory and Returns & Warranty**, with a Postgres event store. This was the decision until 2026-10-03; see the revision note.
- **Debezium change-data capture instead of an outbox relay.** Rejected: Kafka Connect is one more JVM runtime, at about 1 GB, in a stack held to about 8 GB, plus logical-replication setup on Postgres.
- **A hand-written polling relay.** Viable, and the only option that keeps strict per-aggregate order. Rejected in favour of Spring Modulith, whose retries, republishing and incomplete-publication tracking are maintained upstream, once versioned events made ordering a consumer's concern.

## Consequences

- Axon Framework is not part of the stack, and there is no event store ([ADR 0008](./0008-event-store-on-postgres-not-axon-server.md) is withdrawn).
- Every consumer must handle duplicates and out-of-order delivery by version. The same rule makes it safe to rebuild a projection by replaying a topic.
- A service's outbox sends one event at a time, each waiting for Kafka's acknowledgement of the one before, and on startup sends again whatever it holds unsent. That keeps most events in commit order, but not all: sends run on a pool of threads, so two events committed close together can still go out in either order, and a retry or a restart sends an older event after newer ones. A restart can also send an event that another instance is sending at the same moment. One send in flight per instance also caps how fast a service can publish, at about one Kafka round trip per event; a service that outgrows that needs per-aggregate ordering by another means, such as a polling relay.
- Read models are queries over the owning context's tables, or denormalized tables it maintains in the same transaction. Other contexts build their own from integration events.
- Changing an event's shape is a reviewed change to its schema in the repo. The registry refuses a change that breaks backward compatibility.

## Revised 2026-10-03

This ADR used to be "CQRS + event sourcing: four contexts, not fifteen". It event-sourced Order Management, Payment, Inventory and Returns & Warranty for their audit trails and replayable lifecycles, on Axon with a Postgres event store ([ADR 0008](./0008-event-store-on-postgres-not-axon-server.md)). Measured against production needs rather than an "audit trail" in general, none of the four needed it:

- An append-only history table gives the same audit trail, and a transactional outbox the same reliable events, without a framework.
- Order and Returns change in one way only, their status, so their history is short and fully known in advance. Event sourcing pays off when you can't predict which questions you'll ask of past facts.
- Payment and Inventory do need their facts, and a ledger table keeps them.
- With Sagas on Temporal ([ADR 0009](./0009-sagas-on-temporal.md)), a workflow's history already records each process step, so event-sourced aggregates would have been a second record of the same lifecycle.
- Axon 5, the line with a native Postgres event store, does not document Spring Boot 4 support and has no Kafka extension. Axon 4.13 supports Boot 4, but without Axon 5's newer consistency model, which is what a multi-Variant Reservation would have needed.

## Revised 2026-10-06

The Postgres services' outbox now sends events one at a time and sends again, on startup, what it holds unsent. Before, sends ran concurrently, and an event whose send a crash cut short stayed in the outbox until someone resubmitted it by hand. Consumers still order events by version: neither setting makes Kafka's order strict.
