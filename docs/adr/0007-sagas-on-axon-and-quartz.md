# Sagas run on Axon + Quartz, not a dedicated workflow engine

Axon's built-in `@Saga` support, backed by a JPA-persisted saga store on Postgres, covers the order-fulfillment and returns-approval workflows without a new runtime. Long waits — a Warranty Window on an RMA, for instance — use Axon's Quartz-backed `DeadlineManager`, which persists scheduled deadlines to the same Postgres.

## Considered Options

- Temporal — genuinely stronger at visual in-flight-workflow observability and automatic replay/versioning safety when workflow code changes mid-flight, but it's a new runtime to operate for capability this project doesn't need yet.
- AWS Step Functions — a reasonable managed alternative once deployed to AWS, but adds vendor lock-in for no benefit while everything still runs locally.

## Consequences

If saga logic changes often enough that in-flight instances start breaking, that's the concrete trigger to revisit Temporal — not something to build in preemptively.
