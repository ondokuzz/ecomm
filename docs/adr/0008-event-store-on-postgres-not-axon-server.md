# Event store on Postgres, not Axon Server

Axon Framework's event-storage engine is pluggable. A JPA/Postgres-backed store gives the full aggregate/snapshot/projection programming model without adding a new datastore to operate, and with zero paid tier — Axon Server's clustering/HA features sit behind its paid Enterprise Edition, and its Standard Edition would still be one more service to run for capability not yet needed.

## Consequences

Horizontal scaling of event *consumers* is still available without Axon Server, via Axon's JDBC/JPA-backed token store coordinating segment claims across instances. The documented upgrade trigger is command-routing complexity or event-store throughput actually becoming a bottleneck — not a default to reach for early.
