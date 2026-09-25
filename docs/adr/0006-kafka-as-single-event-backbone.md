# Kafka as the single event backbone

Event-sourced contexts need to replay history to rebuild projections — a log-based broker fits that access pattern natively, where a traditional task queue doesn't (messages are consumed and gone unless specially configured). Kafka is also Axon Framework's own officially-supported distributed-messaging extension, the same mechanism that carries a saga's outbound commands across services.

## Considered Options

- RabbitMQ — a fine general-purpose broker, but its queue model is a weaker fit for "replay the whole history to rebuild a projection" than Kafka's log model, and it would mean running two messaging systems instead of one once Kafka is needed for the saga/event-sourcing side anyway.

## Consequences

More operational complexity than RabbitMQ alone, accepted for the replay/log semantics and for keeping a single broker rather than two. Both Kafka and Axon Framework are free and open source — no paid tier required for this choice.
