# Microservices by bounded context, not a monolith

This system spans fifteen distinct problem areas (catalog, cart, order, payment, inventory, fulfillment, returns, and more), and the practice goal is hands-on microservices experience, not just an architecture diagram. Each bounded context ships as its own deployable service, communicating over Kafka and REST rather than in-process calls.

## Considered Options

- A single Spring Boot monolith with modules per context — rejected: doesn't exercise microservices as a real skill, only as a package layout.

## Consequences

Accepted trade-off: real operational overhead (service discovery, per-service config, network calls where a monolith would have a method call) — taken on deliberately, since the practice value is in dealing with those problems, not avoiding them.
