# Orchestration

The Temporal worker for every Saga ([ADR 0009](../../docs/adr/0009-sagas-on-temporal.md)). It owns
no domain data, only its workflows' state in Temporal, so it has no database and no outbox. Built
from [`platform/service-template`](../../platform/service-template/README.md), so its layout,
security and testing conventions apply here.

It has no public API, and the gateway doesn't route it. Over HTTP it serves only
`GET /actuator/health`; it publishes no host port. Other services start its workflows through
Temporal's client, never over HTTP to Orchestration.

## Workflows

| Task queue | Workflow | |
|---|---|---|
| `checkout` | `CheckoutWorkflow` | For now a placeholder that answers with the Checkout Session ID it was given; #60 gives it the checkout Saga's steps |

The workers and their workflow classes are declared in `application.yml` under
`spring.temporal.workers`, through Temporal's Spring Boot starter. Workflow interfaces are inbound
ports (`application.port.in`) and their implementations sit in `application`; HTTP clients stay in
`adapter.out.http`, so workflow code reaches other contexts only through activity interfaces.

Temporal's Java SDK 1.40 and its Spring Boot starter run under Spring Boot 4.1: the starter's
auto-configuration loads, and workflow payloads go through the SDK's own Jackson 2 (2.21, managed
by Boot) beside Boot's Jackson 3. A record holding an `Instant` round-trips. The starter's
dependency on Temporal's test server is left out of the image; only the tests use it.

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

The Saga steps call other contexts with Orchestration's own token, from the confidential
`orchestration` client in the `ecomm` realm, whose service account holds `ORCHESTRATION` ([Identity
& Access ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)).
Every client built by `ServiceRestClients` carries it, as Checkout's internal clients carry
Checkout's:

- the token is fetched with the client credentials and cached in memory;
- it is replaced once it has less than a minute left;
- a 401 drops it, and the call is retried once with a fresh one. No other status is retried here;
  retrying a failed step is Temporal's job.

The secret comes from `ORCHESTRATION_CLIENT_SECRET`, which has no default; Compose sets the dev-only
`orchestration-dev-secret`, matching the realm file.

## Tests

`./gradlew :services:orchestration:check`. The application runs against Temporal's own test server,
reached over gRPC like the real one, and one WireMock server stands in for Keycloak's token endpoint
and the services Orchestration calls:

- `CheckoutWorkerTest`: a workflow started on `checkout` completes;
- `ServiceTokenTest`: the token's caching, renewal and retry on a 401;
- `HealthApiTest`: health is public and nothing else is served;
- `ArchitectureTest`: the shared `HexagonalRules`.
