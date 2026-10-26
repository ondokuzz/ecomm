# Recommendations

"Customers also bought": the Products bought most often together with a Product, filled up with its
Category's best sellers when too few were. Recommendations keeps a projection of Order Management's,
Catalog's and Inventory's integration events in its own Postgres database, `recommendations`, and
works out its Recommendations when asked. It publishes no events. Built from [`platform/service-template`](../../platform/service-template/README.md), so its
layout, security and testing conventions apply here.

## Why Postgres

The answer is a join: from a Product to its Variants, to the Orders that count and bought one, to
the other Variants those Orders bought, to their Products in Stock, grouped; and from a Category to
its Products in Stock and the Orders that bought them. Postgres does each in one query over indexed
tables, and the domain ranks what they return. Each event changes several rows, an Order's and its Variants', or a
Product's and its Variants', and Postgres applies them in one transaction with the version check,
so a query never sees an Order half applied. The stack already runs Postgres, and
`postgres-databases` creates the `recommendations` database on start when it is missing.

## API

| Endpoint | Who | |
|---|---|---|
| `GET /products/{sku}/also-bought?limit=4` | anyone | The Product's Recommendations: what Customers also bought with it, then its Category's best sellers |

```json
{"sku": "AUD-SONY-WH1000XM5", "items": [
  {"sku": "PHN-PIXEL-9", "orders": 3, "reason": "BOUGHT_TOGETHER"},
  {"sku": "AUD-AIRPODS-PRO-2", "orders": 7, "reason": "POPULAR_IN_CATEGORY", "category": "Audio"}]}
```

- Only Orders that **count** are counted: `PAID`, `FULFILLED`, `SHIPPED` or `DELIVERED`, and not
  only `PLACED`, or `CANCELLED` or `RETURNED`, the same rule as
  [Reviews](../reviews-ratings/README.md#who-may-review); and only those placed within
  `ecomm.recommendations.lookback`, 180 days by default.
- First come the other Products in the Orders that bought any of the Product's Variants, with the
  `reason` `BOUGHT_TOGETHER` and `orders`, how many such Orders bought each. They are ranked by
  `orders`, most first, then by the most recently placed of those Orders, then by SKU.
- When fewer than `limit` were bought together, the rest are the Product's Category's best sellers,
  with the `reason` `POPULAR_IN_CATEGORY`, the Category's name as `category`, and `orders`, how
  many Orders bought each at all. They are ranked by `orders`, then by SKU. A Product whose Category
  Recommendations doesn't know yet, or that was removed, gets none.
- Either way, the Product itself, Products already listed, Products removed from the Catalog and
  Products none of whose current Variants is **in Stock** are left out. A Variant is in Stock when
  Inventory stocks it and has some available; one with no Stock event yet is out of Stock, as in
  [Search](../search-discovery/README.md).
- `limit` is from 1 to 12, 4 by default; anything else is a 400 problem detail.
- A SKU Recommendations has never heard of, or with nothing bought with it and no best sellers in
  its Category, has no `items`.

The ranking, with the fill, is the domain's `Recommendations.rank`; the ports only gather the
candidates that pass the exclusions, from the projection's tables.

### Measuring it

```sh
make recommendations-report
```

asks for the Recommendations of every Product Catalog lists, through the Storefront's gateway
routes, and prints the share with at least one and with four, the most the Storefront shows, in all
and by reason. A Recommendation without a reason, from a Sprint 5 stack, counts as bought together,
so the same script reads before and after.

[`http/recommendations.http`](./http/recommendations.http) exercises it against the compose stack.

## The projection

Recommendations consumes four topics, each in its own consumer group, and keeps the newest version of
every aggregate it is sent; a duplicate or an older event is ignored. Each event is applied in one
transaction, which reads the version held, locking its row if there is one, and writes the change only if the event
is newer. An event it can't apply goes to its group's dead-letter topic, such as
`recommendations.orders.dlt`, after its retries, or at once if it can't be read, and
`make dlt-redrive GROUP=<group>` applies it once the cause is fixed
([service template](../../platform/service-template/README.md#consuming)).

| Topic | Group | Becomes |
|---|---|---|
| `order-management.order` | `recommendations.orders` | A row in `orders` per Order: its version, whether it counts and when it was placed; and its Variant IDs in `order_variants` |
| `catalog.product` | `recommendations.products` | A row in `products` per SKU: its version, whether it was removed and its Category's slug; and its Variant IDs in `variants`, those it has now marked `current` |
| `catalog.category` | `recommendations.categories` | A row in `categories` per slug: its version, its name and whether it was removed |
| `inventory.stock` | `recommendations.stock` | A row in `stock` per Variant ID: its version, and whether it is in Stock |

Recommendations are worked out from these tables when they are asked for, so an Order, its Products,
their Categories and their Stock may arrive in any order, and no count needs keeping right as Orders
change Status or Stock runs out. A Product keeps every Variant it has ever had, so an Order for a
Variant Catalog has since dropped still counts as buying its Product; only its current Variants'
Stock says whether it is in Stock. Statuses are read as text: one Order Management adds later reads
as unknown and doesn't count, rather than failing the event. Each event's Correlation ID is in the
logs while it is applied, with a line per event: `Applied Order 0b6f… version 2`, or `Ignored …`.

The consumer groups read their topics from the start when they are new, so the Orders placed before
Recommendations was added count too, and so do the Categories and Stock on a stack upgraded from
Sprint 5.

### Upgrading from Sprint 5

`make up` alone upgrades a Sprint 5 stack. The V2 migration adds the `categories` and `stock`
tables, a Category to each Product, none yet, and marks every Variant held current. The two new
groups read their topics from the start. While any Product is without a Category, the service reads
`catalog.product` from the start whenever its partitions are assigned, and applies each such
Product's event at the version held, as if it were newer, which gives it its Category and its
current Variants. Until then, its Recommendations have no fill.

### Rebuilding

The projection can be rebuilt from the topics, which are compacted, at any time. From the repo root:

```sh
make recommendations-rebuild
```

stops the service, resets its four consumer groups to the earliest offset, apart from their redrive
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

- `AlsoBoughtApiTest`, `ProjectionApiTest`, `StockApiTest`, `CategoryFillApiTest` and
  `UpgradeApiTest` publish Order, Product, Category and Stock events as Order Management, Catalog
  and Inventory would, each checked against its schema first, and read the API over HTTP without a
  token: ranking and its tie-breaks, the limit, the lookback, which Statuses count, a paid Order
  later cancelled, removed Products, a dropped Variant, an Order arriving before its Products,
  Products out of Stock or never stocked, the Category fill with its exclusions, `reason` and
  Category name, a Product kept without a Category on a Sprint 5 stack, and stale and duplicate
  events. Each test Product is in a Category of its own unless the test says otherwise, so other
  tests' Orders never fill it. Postgres, Kafka and the registry run in Testcontainers, each test topic with one
  partition, so a later event applied means the earlier ones are.
- `RecommendationsTest` covers the ranking with the fill, in the domain.
- `ArchitectureTest` runs the shared `HexagonalRules`.
