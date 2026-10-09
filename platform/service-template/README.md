# Service template

A runnable, minimal hexagonal Spring Boot service: `GET /ping` answers with the service name, the
calling Customer's ID, and the current time. Every service under `services/` starts as a copy of
this one.

## Layout

| Package | Holds | May depend on |
|---|---|---|
| `domain` | Aggregates and value objects: pure Java | nothing framework-related (`com.ecomm.commons.money` is fine) |
| `application` | Use cases (`port.in`), the ports they need (`port.out`), and their implementations | `domain`, and `com.ecomm.commons.events` to publish and apply integration events |
| `adapter.in.web` | REST controllers | `application.port.in`, `domain` |
| `adapter.in.kafka` | `@KafkaListener`s for integration events | `application.port.in`, `domain` |
| `adapter.out.<tech>` | Port implementations: databases, HTTP clients, clocks | `application.port.out`, `domain` |

Adapters never touch use-case implementations, only the ports; in and out adapters never depend
on each other.

Use cases are plain classes wired as beans in `UseCaseConfiguration`, so `application` carries no
Spring annotations. Adapters are ordinary Spring components. `ArchitectureTest` runs the shared
`HexagonalRules` from `platform/service-commons` and fails the build on a layering violation.

Errors come back as RFC 7807 problem details via `service-commons`; don't write a per-service
`@ControllerAdvice` for framework errors.

## Correlation IDs and logs

Every request has a Correlation ID, set up by `service-commons` with no code in the service:

- It comes from the `X-Correlation-Id` request header if that has at most 64 characters of
  `[A-Za-z0-9-]`. Otherwise, including when the header is missing, the service generates a UUID.
- It is echoed in the `X-Correlation-Id` response header.
- It is in the MDC under `correlationId` for the rest of the request, so every log line written
  while serving it carries it.
- Every problem detail carries it as `correlationId`, 401 and 403 included, whichever handler
  rendered it, a service's own `@ExceptionHandler` included. That is the support reference a
  Customer sees.
- Every `RestClient` built from Boot's `RestClient.Builder` sends it downstream in
  `X-Correlation-Id`, and logs the call (see below). Build clients from the injected builder
  (`builder.clone()`), not `RestClient.create()`. A call made off the request thread, such as
  from `@Async` code, has no Correlation ID to send.

The Correlation ID is not a security boundary; never use it to decide anything.

Logs are plain text locally and in tests. Under the `docker` profile, `application-docker.yml`
turns on Spring Boot's structured console logging in ECS format (`logging.structured.format.console:
ecs`): one JSON object per line, with the service name (`service.name`, from
`spring.application.name`) and every MDC entry, including `correlationId`. To follow one request
through the compose stack:

```sh
docker compose logs --no-log-prefix | jq -cR 'fromjson? | select(.correlationId == "<id>")'
```

A request's path through the services shows up in three kinds of line:

- **Outbound calls:** every call to another service through such a `RestClient` logs one line
  from `OutboundCallLogInterceptor`, such as `POST http://inventory:8080/reservations -> 409 in
  12 ms`. The query string is left out. A call that gets no response, such as a refused
  connection or a timeout, logs a warning. A client's own retries happen inside the call, so a
  retried call is one line with its final status.
- **State changes:** log a line at `INFO` when a request changes something worth following, as
  Checkout, Cart, Inventory, Order Management and Payment do for each step of a checkout.
- **Errors:** unexpected failures are logged by the shared problem-detail handler.

Services don't log the requests they receive; that access log belongs to the [API gateway](../api-gateway/README.md).

## Integration events

A service tells others what changed by publishing integration events to Kafka, and learns what
changed elsewhere by consuming them ([ADR 0002](../../docs/adr/0002-ledgers-and-outboxes-not-event-sourcing.md),
[ADR 0006](../../docs/adr/0006-kafka-as-single-event-backbone.md)). The template carries what
both need: Postgres, Kafka, Spring Modulith and Apicurio's serializer in `build.gradle.kts`,
Modulith's `event_publication` table in `V1__event_publication.sql`, and the broker and registry in
`application.yml`. `service-commons` configures the rest when they are on the classpath. A service
that only consumes needs only `spring-boot-starter-kafka`.

### Publishing

1. Define the event's schema in [`platform/event-schemas`](../event-schemas/README.md). Its file
   name is the topic, `<context>.<aggregate>`, and Compose creates the topic from it.
2. Describe the event as a record in `application.port.out` that implements `IntegrationEvent`.
   Its components are the event's fields: the aggregate's ID, `version`, `change` and the
   aggregate's state. `topic()` names the topic and `aggregateId()` gives the message key. An empty
   `Optional` or a `null` component is left out.
3. In the use case, call `IntegrationEventPublisher.publish(event)` inside the transaction that
   makes the change, through a `Transactions` port like Inventory's. The use case sees only the
   port, and `HexagonalRules` fails the build if `application` touches Kafka, Apicurio or the outbox.

The publisher adds `eventId` and `occurredAt`, and the Correlation ID of the current request as the
`X-Correlation-Id` header. It records the event in the `event_publication` table, Spring Modulith's
event publication registry, in the same transaction. Kafka gets it once the transaction commits:

- **A rolled-back change publishes nothing**, and publishing outside a transaction throws.
- **Every event is validated** against its topic's latest registered schema when it is produced.
  One that doesn't match never reaches the topic and stays in the table as `FAILED`.
- **A failed send is retried** every 30 seconds (`ecomm.events.outbox.resubmit-failed-every`), and
  incomplete publications are sent again when the service restarts. Until a send succeeds, the row
  shows it:

  ```sql
  SELECT publication_date, status, completion_attempts, serialized_event
  FROM event_publication WHERE completion_date IS NULL;
  ```

Sends go out one at a time, each waiting for Kafka to acknowledge the one before, so events usually
arrive in the order their transactions committed. Delivery is still at least once, and not
strictly in order: sends run on a pool of threads, so two events committed close together can go
out in either order, and a retry or a restart sends an older event after newer ones. Restarting one
of several instances can also resend an event another instance is sending. One send in flight
also caps a service's publishing at about one Kafka round trip per event
([ADR 0002](../../docs/adr/0002-ledgers-and-outboxes-not-event-sourcing.md)).

### Consuming

A listener is an inbound adapter in `adapter.in.kafka`:

```java
@KafkaListener(topics = "catalog.product", groupId = "search-discovery.products")
void on(ProductChanged event) {
  products.apply(event.sku(), event.version(), ...);
}
```

- **Name the group** `<consuming service>.<purpose>`. A new group reads its topics from the start,
  so a projection starts complete.
- **Declare only the fields you need.** The event arrives as the parameter's type, and unknown
  fields are ignored. Consumers never validate against the schema.
- **Apply an event only if it is newer.** In the use case, `Versions.applyIfNewer(event.version(),
  stored, Stored::version, () -> save(...))` runs the change only when nothing is stored yet or
  the event's version is higher. A duplicate or stale event is ignored. Read and write in one
  transaction. A store that has none to offer, such as Search & Discovery's single-node Mongo, may
  read and write one document per aggregate without: one aggregate's events arrive on one
  partition, which one consumer reads in order ([Search ADR 0001](../../services/search-discovery/docs/adr/0001-search-on-mongodb.md)).
- **The event's Correlation ID is in the MDC** while the listener runs, so its log lines carry it,
  as do any calls or events it makes. An event without one gets a new one.
- **A failure is retried** 3 times, after 0.5, 1 and 2 seconds (`ecomm.events.consumer.retries`,
  `ecomm.events.consumer.initial-backoff`). The event is then logged at error level with its
  `eventId`, topic, partition and offset, and skipped, so the partition moves on. An event that
  can't be read at all is skipped at once. Dead-letter topics arrive in Sprint 4. Each retry is
  counted ([Metrics](#metrics)).

### Testing

`EventBackbone`, a `service-commons` test fixture, runs Kafka and Apicurio in Testcontainers, set up
as in Compose. Register it with `EventBackbone.registerWith(registry)` beside `FakeKeycloak`. Before
the application starts, create each topic and register its schema with
`EventBackbone.createTopic(topic)` and `EventBackbone.registerSchemaOf(topic)` (see
`TestInfrastructure`). `registerSchemaOf` reads `event-schemas/<topic>.json` from the classpath:
add `testImplementation(project(":platform:event-schemas"))` for the real topics. The template's
test-only topics keep theirs in `src/test/resources/event-schemas`.

`PublishingEventsTest` and `ConsumingEventsTest` show both sides against a test-only Greeting
topic. Assert on what reaches the topic, read with a plain consumer, not on the publisher's calls.

## Idempotent commands

A command that a caller may send more than once, such as one a Saga step retries, takes an
`Idempotency-Key` header. Declare it on the controller method, and `service-commons` does the rest
in a Postgres service:

```java
@PostMapping("/orders")
@PreAuthorize("hasRole('ORCHESTRATION')")
@IdempotentCommand // or (required = false) to take a key only when the caller sends one
ResponseEntity<OrderResponse> place(@RequestBody PlaceOrderRequest request) { ... }
```

- **A repeat** of the same request (method, path and body bytes; the query string isn't compared) with the same key from the same
  caller gets the recorded status, body, `Content-Type` and `Location` back with
  `Idempotent-Replayed: true`, and the command doesn't run.
- **The same key with a different request** is a 422 problem detail with `reason`
  `idempotencyKeyReused`.
- **A concurrent repeat** waits for the first request to finish, then replays it. A second
  response is never computed.
- **Only a 2xx response is recorded.** Any other status records nothing, so a repeat is evaluated
  afresh.
- **Keys are scoped to the caller**: a client-credentials token's `client_id`, or else its `sub`,
  such as a Customer's ID.
- **A key is 1 to 255 printable ASCII characters.** Anything else is a 400 with `reason`
  `idempotencyKeyInvalid`. A command that requires a key and gets none answers 400 with
  `idempotencyKeyRequired`, but only once its `@PreAuthorize` has let the caller through: a caller
  it refuses gets 403 with or without a key.
- **A role can be exempt** while its caller moves off a command:
  `@IdempotentCommand(exemptRoles = "CHECKOUT")` lets a `CHECKOUT` token act without a key, while
  every other caller still needs one. A key the exempt caller does send is honoured. Order
  Management uses it until Checkout moves onto the checkout Saga.
- **Keys are kept for 7 days** (`ecomm.idempotency.keep-for`), matching Temporal's retention, and
  pruned daily (`ecomm.idempotency.prune-every`).

The key is recorded in the same transaction as the command's change. A filter opens that
transaction around the controller, claims the key in it, and records the response before it
commits. The use case's own transaction joins it, whether through a JDBC-backed `Transactions` port like
Inventory's or a `TransactionTemplate` as in `GreetingCommands`, so the use case sees no
idempotency type, and
`HexagonalRules` fails the build if `application` or `domain` reaches into
`com.ecomm.commons.idempotency`. The response is sent only after the commit. If the transaction
was marked for rollback, as when the use case threw, it rolls back. Otherwise the change commits as
it would without a key, and a non-2xx response gives the key up. A request for which another one
holds the key waits on that key's row until the other commits or rolls back.

Each service adds the key table in its own Flyway migration: copy `V2__idempotency_key.sql`, as
`V1__event_publication.sql` is copied for the outbox. Only a service that declares an
`@IdempotentCommand` prunes keys, so one without the table never touches it. Read a command's
body as JSON: the filter reads the body before the handler does, so form parameters aren't seen.

`IdempotencyApiTest` shows each behaviour through the test-only `GreetingCommands`, and
`IdempotencyKeyPruningApiTest` shows pruning with the retention shortened to a second.

## Metrics

`service-commons` brings Micrometer's Prometheus registry, so every service, the gateway included,
serves `/actuator/prometheus` with no code of its own. It needs no token, and the gateway doesn't
route it: Prometheus scrapes each service over the Compose network. Every metric carries the
service's `spring.application.name` as its `service` tag (`MetricsDefaults`). Every service
exports:

- **HTTP:** Spring's request metrics, `http_server_requests_seconds`, per `method`, `uri` (the
  route's pattern), `status` and `outcome`, with histogram buckets for percentiles. A request
  refused before it reaches a controller, such as a 401, has the `uri` `UNKNOWN`.
- **Kafka consumers:** the consumer client's metrics, `kafka_consumer_*`, each tagged with its
  consumer `group`, among them `kafka_consumer_fetch_manager_records_lag` per `topic` and
  `partition`, and `kafka_consumer_fetch_manager_records_consumed_total` per `topic`. A
  partition's metrics show up within a minute of it being assigned. The `group` tag comes from
  Kafka's default client ID, so don't set `spring.kafka.client-id` or a listener's
  `clientIdPrefix`.
- **Retries:** `ecomm_events_retried_total`, each retry of an event a listener failed on, per
  `group` and `topic`.
- **Outbox:** in a Postgres service with the outbox (Spring Modulith's event publication
  registry), `ecomm_outbox_incomplete_publications`, the events not yet sent to Kafka, per
  `status`: `published` and `processing` are on their way, `failed` and
  `resubmitted` wait for the next resubmission.
- The JVM's, Hikari's, Tomcat's and the Kafka producer's own metrics, as Boot binds them.

A new service is added to Prometheus's targets in
[`infra/docker/prometheus/prometheus.yml`](../../infra/docker/prometheus/prometheus.yml). Grafana's
**Services** and **Event consumers** dashboards show these
([README](../../README.md#metrics-and-dashboards)). `MetricsApiTest` asserts the names.

## Security

`service-commons` makes every service an OAuth2 resource server that accepts only Keycloak-issued
JWTs (`JwtResourceServerAutoConfiguration`):

- Every request needs a valid bearer token except `/actuator/health` and `/actuator/prometheus`
  ([Metrics](#metrics)). A missing or invalid token
  gets 401, a missing role gets 403, and both come back as problem details.
- Guard role-restricted operations with `@PreAuthorize("hasRole('STAFF')")`. Realm roles from
  `realm_access.roles` become `ROLE_` authorities.
- Declare a `CurrentCustomer` controller parameter to get the calling Customer. Its `id` is the
  token's `sub`. Pass the plain ID into use cases; security types stay out of `application` and
  `domain`.
- Keys come from `ecomm.security.jwt.jwk-set-uri`, and `iss` must equal
  `ecomm.security.jwt.issuer-uri`, which defaults to `http://localhost:8180/realms/ecomm`. Outside
  Docker the JWK set URI is derived from the issuer; `application-docker.yml` points it at
  `keycloak:8080`.
- To let anyone `GET` some paths without a token, such as a public product listing, list them as
  path patterns under `ecomm.security.public-read-paths`. Other methods on those paths still need
  a token.
- A service that needs other URL rules declares its own `SecurityFilterChain` bean, which replaces
  the default. Build it on `ResourceServerSecurity.configure(http, handlerExceptionResolver)` to
  keep the token validation and problem-detail 401s and 403s, and add only the rules.
- Behind the [API gateway](../api-gateway/README.md), a browser's request has already had its token
  checked, but the service checks it again and makes every role and ownership decision itself
  ([ADR 0010](../../docs/adr/0010-api-gateway-authenticates-at-the-edge.md)). List a new public read
  or internal endpoint in the gateway's route table too.

An internal endpoint, one that only another service calls, is guarded by the calling service's own
role, such as `@PreAuthorize("hasRole('CHECKOUT')")`. The caller authenticates with its own
client-credentials token ([ADR 0002](../../services/identity-access/docs/adr/0002-service-identity-by-client-credentials.md)),
so the token's `sub` is the service, not a Customer. When the call is about a Customer, it names
them as a `customerId` in the body. Take the Customer from the body only on endpoints guarded that
way; everywhere else the Customer is the token's `sub`, via `CurrentCustomer`. Test the allowed
caller with `FakeKeycloak.token("checkout", "CHECKOUT")`, and test that a `CUSTOMER` token gets 403.

## Tests

Test from the outside, at the HTTP seam: start the app (`@SpringBootTest(webEnvironment =
RANDOM_PORT)` with `@AutoConfigureRestTestClient`), send real requests with `RestTestClient`, and
assert on status and body only (see `PingApiTest`). Authenticate with tokens from `FakeKeycloak`
(a `service-commons` test fixture): register it with `@DynamicPropertySource` and send
`FakeKeycloak.token("customer-id", "CUSTOMER")` as the bearer token. A
service with a datastore runs it in Testcontainers; the template's tests share one Postgres,
Kafka and registry through `TestInfrastructure`. Pure domain tests are the exception, for dense
rules such as state transitions. Integration events have a second seam, the topic: assert on what
a change publishes there, and on what a consumer makes of the events sent to it (see
[Testing](#testing)).

## Creating a new service

1. Copy this directory to `services/<context>` and delete `build/` if present. A service that
   neither publishes events nor keeps data in Postgres drops those dependencies, the migrations
   and the datasource and Kafka settings. One with no `@IdempotentCommand` drops
   `V2__idempotency_key.sql`.
2. Rename the base package `com.ecomm.template` to `com.ecomm.<context>` (no hyphens) and the
   application class, and update `@AnalyzeClasses` in `ArchitectureTest`.
3. Set `spring.application.name` in `application.yml` and the expected name in `PingApiTest`
   (or replace ping with the service's first real endpoint).
4. Set `MODULE` in the `Dockerfile` to `services/<context>`. Name its database in
   `spring.datasource.url` and add it to `infra/docker/postgres-databases.sh`.
5. Add `":services:<context>"` to the root `settings.gradle.kts`.
6. Run `./gradlew :services:<context>:check`.
7. Add a committed `http/<context>.http` file for exercising the endpoints by hand.

## Run it

```sh
docker compose up -d --wait keycloak kafka apicurio   # from the repo root
docker compose exec postgres createdb -U ecomm service_template
./gradlew :platform:service-template:bootRun
TOKEN=$(curl -s http://localhost:8180/realms/ecomm/protocol/openid-connect/token \
  -d grant_type=password -d client_id=dev-cli \
  -d username=demo@ecomm.local -d password=demo | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" localhost:8080/ping

docker build -f platform/service-template/Dockerfile -t ecomm/service-template .
docker run --rm -p 8080:8080 ecomm/service-template
```
