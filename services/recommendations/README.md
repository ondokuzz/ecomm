# Recommendations

"Customers also bought": the Products bought most often together with a Product. Recommendations
keeps a projection of Order Management's and Catalog's integration events in its own Postgres
database, `recommendations`, and works out what was bought together when asked. It publishes no
events. Built from [`platform/service-template`](../../platform/service-template/README.md), so its
layout, security and testing conventions apply here.

## Why Postgres

The answer is a join: from a Product to its Variants, to the Orders that count and bought one, to
the other Variants those Orders bought, to their Products, grouped and ranked. Postgres does that in
one query over indexed tables. Each event changes several rows, an Order's and its Variants', or a
Product's and its Variants', and Postgres applies them in one transaction with the version check,
so a query never sees an Order half applied. The stack already runs Postgres, and
`postgres-databases` creates the `recommendations` database on start when it is missing.

## API

| Endpoint | Who | |
|---|---|---|
| `GET /products/{sku}/also-bought?limit=4` | anyone | What Customers also bought with the Product |

```json
{"sku": "AUD-SONY-WH1000XM5", "items": [{"sku": "PHN-PIXEL-9", "orders": 3}, {"sku": "LPT-XPS-13", "orders": 1}]}
```

- `items` are the other Products in the Orders that **count** and bought any of the Product's
  Variants, with `orders`, how many such Orders bought each. An Order counts once it is `PAID`,
  `FULFILLED`, `SHIPPED` or `DELIVERED`; one only `PLACED`, or `CANCELLED` or `RETURNED`, doesn't,
  the same rule as [Reviews](../reviews-ratings/README.md#who-may-review).
- They are ranked by `orders`, most first, then by the most recently placed of those Orders, then
  by SKU.
- The Product itself is left out, and so is a Product removed from the Catalog.
- `limit` is from 1 to 12, 4 by default; anything else is a 400 problem detail.
- A SKU Recommendations has never heard of, or that nothing was bought with, has no `items`.

[`http/recommendations.http`](./http/recommendations.http) exercises it against the compose stack.

## The projection

Recommendations consumes two topics, each in its own consumer group, and keeps the newest version of
every aggregate it is sent; a duplicate or an older event is ignored. Each event is applied in one
transaction, which reads the version held, locking its row if there is one, and writes the change only if the event
is newer. An event it can't apply goes to its group's dead-letter topic, such as
`recommendations.orders.dlt`, after its retries, or at once if it can't be read, and
`make dlt-redrive GROUP=<group>` applies it once the cause is fixed
([service template](../../platform/service-template/README.md#consuming)).

| Topic | Group | Becomes |
|---|---|---|
| `order-management.order` | `recommendations.orders` | A row in `orders` per Order: its version, whether it counts and when it was placed; and its Variant IDs in `order_variants` |
| `catalog.product` | `recommendations.products` | A row in `products` per SKU: its version and whether it was removed; and its Variant IDs in `variants` |

What was bought together is worked out from these tables when it is asked for, so an Order and its
Products may arrive in either order, and no count needs keeping right as Orders change Status. A
Product keeps every Variant it has ever had, so an Order for a Variant Catalog has since dropped
still counts as buying its Product. Statuses are read as text: one Order Management adds later reads
as unknown and doesn't count, rather than failing the event. Each event's Correlation ID is in the
logs while it is applied, with a line per event: `Applied Order 0b6f… version 2`, or `Ignored …`.

The consumer groups read their topics from the start when they are new, so the Orders placed before
Recommendations was added count too.

### Rebuilding

The projection can be rebuilt from the topics, which are compacted, at any time. From the repo root:

```sh
make recommendations-rebuild
```

stops the service, resets both consumer groups to the earliest offset, apart from their redrive
topics, which are moved to their end, empties its tables, and starts it again ([`replay-groups.sh`](../../infra/docker/replay-groups.sh) fails
the command, before anything is emptied, if Kafka refuses a reset). `make seed-reset`
drops and recreates the whole `recommendations` database instead, since the topics are emptied.

## Run it

```sh
docker compose up -d --build recommendations   # from the repo root; listens on localhost:8098
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/recommendations/`, which lets the
public read through without a token.

Or run Postgres, Keycloak and the event backbone in compose (`docker compose up -d postgres-databases
keycloak kafka-topics`) and start the service with `./gradlew :services:recommendations:bootRun` on
port 8080.

Since Sprint 5 its database password is a Compose secret, mounted as the file
`spring.datasource.password` (see the root README's "Secrets"); `application.yml` has none. Outside
Docker, give it as `SPRING_DATASOURCE_PASSWORD="$(cat .secrets/ecomm/postgres-password)"`.

## Tests

- `AlsoBoughtApiTest` and `ProjectionApiTest` publish Order and Product events as Order Management
  and Catalog would, each checked against its schema first, and read the API over HTTP without a
  token: ranking and its tie-breaks, the limit, which Statuses count, a paid Order later cancelled,
  removed Products, a dropped Variant, an Order arriving before its Products, and stale and
  duplicate events. Postgres, Kafka and the registry run in Testcontainers, each test topic with one
  partition, so a later event applied means the earlier ones are.
- `ArchitectureTest` runs the shared `HexagonalRules`.
