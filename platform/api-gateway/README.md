# API gateway

The one way in for browsers. A Spring Cloud Gateway service in its servlet (WebMVC) flavour, built
on the [service template](../service-template/README.md) conventions and `service-commons`. It is a
platform module, not a bounded context: it has no domain, so it has no hexagonal layers either.
In compose it listens on host port 8000, and the Storefront's nginx sends it everything under
`/api/`.

It does three things and nothing more ([ADR 0010](../../docs/adr/0010-api-gateway-authenticates-at-the-edge.md)):
**routes**, **authenticates at the edge**, and **hides internal endpoints**. Roles and ownership
stay with the services, which still check every token themselves.

## Routes

`/api/<service>/**` goes to that service with the prefix stripped, query included, so
`GET /api/catalog/products?category=phones` reaches Catalog as `GET /products?category=phones`.

| Service | Public reads (`GET`, no token) | Internal, never routed |
|---|---|---|
| `catalog` | `/products/**`, `/variants/**`, `/categories/**` | |
| `inventory` | `/stock/*` | `/reservations/**`, and `/stock/decrement`, which Inventory no longer has |
| `cart` | | |
| `checkout-pricing` | | |
| `order-management` | | `POST /orders`, `PATCH /orders/*/status` |
| `promotions` | | `/discounts/**`: Coupon evaluation, which only Checkout calls |

The table lives in [`application.yml`](./src/main/resources/application.yml) under
`ecomm.gateway.services`, and routing and edge security are both built from it (`EdgeRoutes`), so
they can't disagree. Adding a service is one entry: its `uri`, and any `public-reads` and
`internal` paths, written as the service's own paths. An `internal` entry is a path pattern, for
every method, or a method and a pattern. `application-docker.yml` points each `uri` at the compose
network; outside Docker they are the services' dev host ports.

- **Public reads** pass without a token. A token that is sent is still checked, and forwarded.
- **Everything else routed** needs a valid `ecomm` token: the same JWT validation as every
  service, from `service-commons`. Without one the gateway answers a 401 problem detail and the
  service never sees the request. `Authorization` is forwarded unchanged, so the service checks
  the same token and applies its own roles.
- **Internal endpoints**, those only other services call, are not routed and get a 404, token or
  not, as does any path outside the table. Payment has no routes at all, so `POST /payments` is
  among them. Hiding Promotions' `POST /discounts/evaluate` means nobody can probe Coupon codes
  outside a checkout; Staff still reach `/coupons` through the gateway.

The gateway sends no CORS headers: browsers reach it on the Storefront's own origin.

## Correlation IDs and the access log

For a browser the Correlation ID is born just before the gateway, in the Storefront's nginx, so its
own error pages carry it when the gateway is down ([Storefront](../../frontend/storefront/README.md#error-states));
for any other caller the gateway is where it is born. It applies the same rule as every
service (`service-commons`): the caller's `X-Correlation-Id` if it is at most 64 characters of
`[A-Za-z0-9-]`, a new UUID otherwise. It sets that ID on the forwarded request, replacing whatever
the caller sent, and on the response, once, even though the service echoes it too. The 401 and 404
problem details carry it as `correlationId`. The forwarded request gets it the way every service's
outbound call does: the gateway forwards through a `RestClient` built from Boot's builder, which
`service-commons` customizes.

Every request leaves one access line, with the Correlation ID in the MDC (and so in the JSON under
the `docker` profile, and in brackets in plain-text logs):

```
GET /api/cart/cart -> 401 in 3 ms
```

The path is logged without its query. Health checks under `/actuator/` are left out. A forwarded
request also leaves the usual outbound-call line, which names the service and how long it took
(`GET http://cart:8080/cart -> 200 in 12 ms`). The services don't log the requests they receive;
this is their access log.

## Tests

HTTP-seam tests start the gateway with one WireMock server standing in for every service
(`GatewayApiTest`) and tokens from `FakeKeycloak`:

- `RoutingApiTest`: each service is reached with the prefix stripped; internal and unknown
  endpoints are 404s that never reach the stub; no CORS headers.
- `EdgeAuthenticationApiTest`: public reads pass without a token; every other route is a 401
  without a valid one and never reaches the stub; `Authorization` is forwarded untouched.
- `CorrelationIdApiTest`: a valid incoming ID is kept and a malformed one replaced, on both the
  forwarded request and the response; the access log line.

```sh
./gradlew :platform:api-gateway:check
```

## Run it

In compose it starts with the rest of the stack (`make up`), on http://localhost:8000, after the
services it routes to. [`http/api-gateway.http`](./http/api-gateway.http) walks through a public
read, a 401, a forwarded token, Correlation IDs and the hidden internal endpoints.

Each service keeps its own host port (8081–8086) for its `.http` file. Those bypass the gateway:
they are for development only, and nothing in a browser uses them.

Outside Docker, with the services on their host ports:

```sh
./gradlew :platform:api-gateway:bootRun --args=--server.port=8000
```
