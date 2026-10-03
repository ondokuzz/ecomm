---
status: withdrawn
---

# Event store on Postgres, not Axon Server

Withdrawn on 2026-10-03: no context is event-sourced, so there is no event store to place ([ADR 0002](./0002-ledgers-and-outboxes-not-event-sourcing.md)).

It decided that Axon Framework's event store would sit on Postgres through JPA, rather than on Axon Server, whose clustering and high availability are paid Enterprise features. That gave the aggregate, snapshot and projection model without another datastore to run.
