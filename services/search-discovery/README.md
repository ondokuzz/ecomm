# Search & Discovery

Customers search and filter the Catalog. Search is a read projection over Catalog's and
Inventory's integration events, kept in its own MongoDB database, `search`; it owns no data of its
own and publishes no events. Built from [`platform/service-template`](../../platform/service-template/README.md),
so its layout, security and testing conventions apply here. Why Mongo's text index is enough for now
is in [ADR 0001](./docs/adr/0001-search-on-mongodb.md).

## API

| Endpoint | Who | |
|---|---|---|
| `GET /search` | anyone | A page of Product summaries matching a search, with how many matched and the facets |
| `GET /products/{sku}` | anyone | [One Product](#one-product), with each Variant's Price and whether it is in Stock |

[`http/search-discovery.http`](./http/search-discovery.http) exercises them against the compose
stack.

### Asking

Every parameter is optional, and a Product must match all of them:

| Parameter | Matches |
|---|---|
| `q` | Words in the name, the description or any attribute or axis value, by Mongo's text index: any of the words, stemmed in English, so `phones` finds `phone` |
| `category` | A Category's slug |
| `attr.<name>=<value>` | An `ENUM`, `TEXT` or `BOOLEAN` attribute having the value: the Product's own, or any Variant's for a Variant axis. Repeat it for several values, any of which will do: `attr.storage=128 GB&attr.storage=256 GB` |
| `range.<name>=<min>..<max>` | A `NUMBER` attribute with a value in the range, both ends included; leave one out for an open end, as in `range.weight=..1.5` |
| `currency`, `price=<min>..<max>` | Any Variant's Price in `currency` within the range, in its Minor unit: `currency=EUR&price=50000..90000` is €500.00 to €900.00. `currency` alone keeps the Products priced in it |
| `inStock=true` | Any Variant in Stock |
| `sort` | `relevance` (the default: best text match first, and newest first without `q`), `newest`, `price-asc` or `price-desc`, by the "from" Price |
| `page`, `size` | The page, from 0, and how many Products it holds, 1 to 100; 24 by default |
| `facets=false` | Nothing: it leaves `facets` out of the answer and skips counting them, for a caller that shows none, such as the Support Assistant |

Each filter is checked against the Product's Variants on its own: a phone with a €699 Variant out of
Stock and a €799 one in Stock matches `price=..70000&inStock=true`.

A search that can't match as written, such as an unknown `sort`, a range whose min is above its max,
a range end that isn't a number, or a `price` without its `currency`, is a 400 problem detail.

Attribute filters use `attr.` and `range.` prefixes, rather than one parameter per attribute, so
any attribute a Category defines can be filtered on, and a name holding a dot stays unambiguous.

### The answer

```json
{"items": [{"sku": "PHN-PIXEL-9", "name": "Google Pixel 9",
            "image": "/images/products/phn-pixel-9/front.svg",
            "priceFrom": {"amountMinor": 79900, "currency": "EUR"}, "priceVaries": true,
            "inStock": true, "attributes": {"brand": "Google", "screen": "6.3 in"}}],
 "page": 0, "size": 24, "total": 1,
 "facets": {
   "categories": [{"slug": "audio", "name": "Audio", "count": 0},
                  {"slug": "phones", "name": "Phones", "count": 1}],
   "attributes": [{"name": "storage", "type": "ENUM", "min": null, "max": null,
                   "values": [{"value": "128 GB", "count": 1}, {"value": "1 TB", "count": 0}]}],
   "prices": [{"currency": "EUR", "min": 79900, "max": 89900}],
   "inStock": 1}}
```

A summary is what a Product card needs: the Product's first image, or else its first Variant's own
(`null` with none); its lowest Variant Price, `priceVaries` when the Variants' Prices differ, so a
card shows it as "from"; whether any Variant is in Stock; and the Product's own attributes.

### Facets

Each [Facet](../../GLOSSARY.md) is counted in Products, as if its own filter weren't applied
(disjunctive counting): with `attr.storage=128 GB` chosen, `256 GB`'s count is how many Products
choosing it as well would add. Every other filter applies.

- **`categories`**: every Category, with how many Products in it match, by name. A Category with none
  is listed at 0; one that Products name but whose event hasn't arrived is listed by its slug.
- **`attributes`**: once a `category` is chosen, one per Attribute definition, in its order. An
  `ENUM`'s values come in its definition's order, every one listed even at 0, then any others its
  Products have; a `TEXT`'s or `BOOLEAN`'s values come alphabetically. A chosen value is listed even
  when no Product has it, so it can be cleared. A `NUMBER` has no values, but the lowest and highest
  value Products have (`null` with none).
- **`prices`**: the lowest and highest Variant Price in each Currency.
- **`inStock`**: how many Products have a Variant in Stock.

## One Product

`GET /products/{sku}` answers one Product from the projection, which already joins Catalog's
Products and Categories with Inventory's Stock, so one read answers what would otherwise take
Catalog, then Inventory per Variant:

```json
{"sku": "PHN-PIXEL-9", "name": "Google Pixel 9", "category": {"slug": "phones", "name": "Phones"},
 "attributes": {"brand": "Google", "screen": "6.3 in"},
 "warranty": {"months": 24, "manufacturer": "Google"},
 "variants": [{"id": "PHN-PIXEL-9", "axisValues": {"color": "Obsidian", "storage": "128 GB"},
               "price": {"amountMinor": 79900, "currency": "EUR"}, "inStock": true},
              {"id": "PHN-PIXEL-9-OBSIDIAN-256",
               "axisValues": {"color": "Obsidian", "storage": "256 GB"},
               "price": {"amountMinor": 89900, "currency": "EUR"}, "inStock": false},
              {"id": "PHN-PIXEL-9-PORCELAIN-128",
               "axisValues": {"color": "Porcelain", "storage": "128 GB"},
               "price": {"amountMinor": 79900, "currency": "EUR"}, "inStock": true}]}
```

`category` is its Category's slug and name, the slug again while the Category's event hasn't
arrived, as the facets list it; `warranty` is its [Warranty terms](../../GLOSSARY.md), `null` when
it has none. Each Variant says whether it is in Stock, as a search does, but not how many.
A SKU the projection doesn't hold, or holds removed, is a 404 problem detail. The answer may lag
Catalog and Inventory by an event, as every search does.

## The projection

Search consumes three topics, each in its own consumer group, and keeps the newest version of every
aggregate it is sent; a duplicate or an older event is ignored. An event it can't apply goes to its
group's dead-letter topic, such as `search-discovery.products.dlt`, after its retries, or at once
if it can't be read, and `make dlt-redrive GROUP=<group>` applies it once the cause is fixed
([service template](../../platform/service-template/README.md#consuming)).

| Topic | Group | Becomes |
|---|---|---|
| `catalog.product` | `search-discovery.products` | A document in `products` per SKU |
| `catalog.category` | `search-discovery.categories` | A document in `categories` per slug |
| `inventory.stock` | `search-discovery.stock` | A document in `stock` per Variant ID |

- A Product is searchable as soon as its event is applied. A removed one, or a removed Category, is
  kept marked `removed`, with its version, so a stale event can't bring it back, but it is never found
  or listed. A SKU created again carries on from its last version, and is found again.
- A Product keeps its Warranty terms from Catalog's event; one applied before Search projected them
  has none until its next event, or `make search-rebuild`, which replays every Product's latest.
- A Variant is in Stock when Inventory stocks it and has some available. One with no Stock event yet
  counts as out of Stock.
- "Newest" orders by when a Product was listed: the time of the event that made it searchable, kept
  while it stays listed. A rebuild replays only each Product's latest event, so after one, every
  Product counts as listed when it last changed.
- Each event's Correlation ID is in the logs while it is applied, with a line per event: `Applied
  Product PHN-PIXEL-9 version 3`, or `Ignored …` for a duplicate or stale one.

One aggregate's events arrive on one partition, which one consumer reads in order, so applying an
event reads and writes a single document without a transaction. Mongo runs as a single node here,
with no replica set, and so has no multi-document transactions to offer.

### Storage

`products` holds each Product as Catalog published it: its attributes and each Variant's axis values
as `{"name", "value"}` pairs, which keeps Staff's own attribute names safe from Mongo's dots and `$`,
its Warranty terms as `warranty`, `null` with none, plus `attributeValues`, every value flattened
for the text index, `removed` and `listedAt`. On startup the service creates `text`, the text index
searches run on: the name weighted 10, `attributeValues` 5 and the description 1. A search looks up
its candidates with one aggregation: the text match, then each Product's Stock, joined from `stock`
by Variant ID. One Product is read the same way, matched by its SKU, with its Category joined from
`categories` by slug as well.

### Rebuilding

The topics are compacted, so they always hold every aggregate's latest event, and the projection can
be rebuilt from them at any time: stop the service, reset its consumer groups to the earliest
offset, drop its collections, and start it again. Each group's redrive topic is moved to its end instead,
since what was redriven is still on the topic it came from. From the repo root:

```sh
make search-rebuild
```

which runs:

```sh
docker compose stop search-discovery
infra/docker/replay-groups.sh search-discovery.products search-discovery.categories search-discovery.stock
docker compose exec -T mongo mongosh --quiet search --eval 'db.products.drop(); db.categories.drop(); db.stock.drop()'
docker compose up -d --wait search-discovery
```

[`replay-groups.sh`](../../infra/docker/replay-groups.sh) waits up to a minute for each group to be
empty, since a consumer killed while closing stays a member until the broker's session timeout, and
Kafka refuses to reset a group that has members. It fails, before anything is dropped, if a group
isn't empty or a reset is refused: `kafka-consumer-groups.sh` itself says so but exits 0.

The groups can only be reset while the service is stopped. `make seed-reset` drops the `search`
database and empties the topics instead, since Catalog's and Inventory's versions start again from 1
after a reset.

## Run it

```sh
docker compose up -d --build search-discovery   # from the repo root; listens on localhost:8089
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/search-discovery/`.

Or run Mongo, Keycloak and the event backbone in compose (`docker compose up -d mongo keycloak
kafka-topics`) and start the service with `./gradlew :services:search-discovery:bootRun` on port
8080.

## Tests

- `SearchApiTest`, `ProjectionApiTest`, `FacetsApiTest` and `ProductApiTest` publish events to the
  three topics as Catalog and Inventory would, each checked against its schema first, and search or
  read one Product over HTTP: text, Category, attribute, numeric, price and in-stock filters, each
  sort, paging, 400s, removals, Price changes, stale and duplicate events, Stock, disjunctive facets
  and searches without them, and one Product's Variants, Stock, Warranty terms and 404s. Mongo,
  Kafka and the registry run in Testcontainers.
- `FacetCountingTest` is a pure domain test of the facet counting in `Search`.
- `ArchitectureTest` runs the shared `HexagonalRules`.
