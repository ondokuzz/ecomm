# Service template

A runnable, minimal hexagonal Spring Boot service: `GET /ping` answers with the service name and
the current time. Every service under `services/` starts as a copy of this one.

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

## Tests

Test from the outside, at the HTTP seam: start the app (`@SpringBootTest(webEnvironment =
RANDOM_PORT)` with `@AutoConfigureRestTestClient`), send real requests with `RestTestClient`, and
assert on status and body only (see `PingApiTest`). A
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
./gradlew :platform:service-template:bootRun
curl localhost:8080/ping

docker build -f platform/service-template/Dockerfile -t ecomm/service-template .
docker run --rm -p 8080:8080 ecomm/service-template
```
