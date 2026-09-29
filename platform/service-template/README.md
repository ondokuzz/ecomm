# Service template

A runnable, minimal hexagonal Spring Boot service: `GET /ping` answers with the service name, the
calling Customer's ID, and the current time. Every service under `services/` starts as a copy of
this one.

## Layout

| Package | Holds | May depend on |
|---|---|---|
| `domain` | Aggregates and value objects: pure Java | nothing framework-related (`com.ecomm.commons.money` is fine) |
| `application` | Use cases (`port.in`), the ports they need (`port.out`), and their implementations | `domain` |
| `adapter.in.web` | REST controllers | `application.port.in`, `domain` |
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
  from `OutboundCallLogInterceptor`, such as `POST http://inventory:8080/stock/decrement -> 409 in
  12 ms`. The query string is left out. A call that gets no response, such as a refused
  connection or a timeout, logs a warning. A client's own retries happen inside the call, so a
  retried call is one line with its final status.
- **State changes:** log a line at `INFO` when a request changes something worth following, as
  Checkout, Cart, Inventory, Order Management and Payment do for each step of a checkout.
- **Errors:** unexpected failures are logged by the shared problem-detail handler.

Services don't log the requests they receive; that access log belongs to the [API gateway](../api-gateway/README.md).

## Security

`service-commons` makes every service an OAuth2 resource server that accepts only Keycloak-issued
JWTs (`JwtResourceServerAutoConfiguration`):

- Every request needs a valid bearer token except `/actuator/health`. A missing or invalid token
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
service with a datastore runs it in Testcontainers. Pure domain tests are the exception, for dense
rules such as state transitions.

## Creating a new service

1. Copy this directory to `services/<context>` and delete `build/` if present.
2. Rename the base package `com.ecomm.template` to `com.ecomm.<context>` (no hyphens) and the
   application class, and update `@AnalyzeClasses` in `ArchitectureTest`.
3. Set `spring.application.name` in `application.yml` and the expected name in `PingApiTest`
   (or replace ping with the service's first real endpoint).
4. Set `MODULE` in the `Dockerfile` to `services/<context>`.
5. Add `":services:<context>"` to the root `settings.gradle.kts`.
6. Run `./gradlew :services:<context>:check`.
7. Add a committed `http/<context>.http` file for exercising the endpoints by hand.

## Run it

```sh
docker compose up -d keycloak          # from the repo root
./gradlew :platform:service-template:bootRun
TOKEN=$(curl -s http://localhost:8180/realms/ecomm/protocol/openid-connect/token \
  -d grant_type=password -d client_id=dev-cli \
  -d username=demo@ecomm.local -d password=demo | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" localhost:8080/ping

docker build -f platform/service-template/Dockerfile -t ecomm/service-template .
docker run --rm -p 8080:8080 ecomm/service-template
```
