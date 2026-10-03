# Kafka as the single event backbone

Kafka carries every integration event between contexts: the facts a context publishes from its transactional outbox ([ADR 0002](./0002-ledgers-and-outboxes-not-event-sourcing.md)), for whichever contexts care, such as Search & Discovery, Reviews & Ratings, Recommendations and Notifications. A log-based broker fits that access pattern: a new consumer, or a rebuilt projection, can read a topic from the start, where a traditional task queue consumes messages and drops them unless specially configured.

Kafka carries events, not commands. A Saga step that needs another context to act calls that context's command API directly ([ADR 0009](./0009-sagas-on-temporal.md)).

## Considered Options

- RabbitMQ — a fine general-purpose broker, but its queue model is a weaker fit for "read the whole history to rebuild a projection" than Kafka's log model.

## Consequences

More operational complexity than RabbitMQ alone, accepted for the log semantics. Kafka is free and open source, with no paid tier needed for this choice, and so is the Apicurio registry that holds the events' schemas.

## Revised 2026-10-03

Kafka was also to carry Saga commands and their replies, and to be the transport behind Axon Framework's distributed messaging. Neither holds any more. No context is event-sourced, so Axon is gone ([ADR 0002](./0002-ledgers-and-outboxes-not-event-sourcing.md)). Saga steps call command APIs directly, because Temporal already retries them durably ([ADR 0009](./0009-sagas-on-temporal.md)). Commands over Kafka would have needed a command topic and a consumer per context, a reply listener that maps each reply back to its workflow, and deduplication on both sides, all for decoupling that Temporal already gives. The "replay to rebuild projections" reason now rests on integration events alone.
