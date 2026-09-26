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
- A service that needs different URL rules, such as public endpoints, declares its own
  `SecurityFilterChain` bean, which replaces the default.

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
