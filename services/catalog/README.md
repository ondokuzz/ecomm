# Catalog

Products, their Variants, Categories and Price. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Data lives in Couchbase ([ADR 0001](./docs/adr/0001-catalog-on-couchbase.md)).
Every change to a Product or a Category is published as a `catalog.product` or `catalog.category`
event, through an outbox of its own in Couchbase ([ADR 0002](../../docs/adr/0002-ledgers-and-outboxes-not-event-sourcing.md)).

## API

| Endpoint | Who | |
|---|---|---|
| `GET /products?category=` | anyone | Products ordered by name, all of them when `category` is left out |
| `GET /products/{sku}` | anyone | One Product, 404 when missing |
| `GET /variants/{variantId}` | anyone | One Variant with its Product's SKU, name, Category and images, 404 when missing |
| `GET /categories` | anyone | Categories ordered by slug, with name, Product count and attribute definitions |
| `GET /categories/{slug}` | anyone | One Category, 404 when missing |
| `GET /currencies` | anyone | The Currencies a Price can be in, ordered by code, each with its Minor unit's digits |
| `POST /products` | Staff | 201, or 409 when the SKU or a Variant ID is taken |
| `PUT /products/{sku}` | Staff | Replaces the Product and its Variants; 404 when missing, 409 when it drops one of its Variant IDs or takes another Product's |
| `DELETE /products/{sku}` | Staff | 204; 404 when missing |
| `POST /categories` | Staff | 201, or 409 when the slug is taken |
| `PUT /categories/{slug}` | Staff | Replaces the name and definitions; 404 when missing |
| `DELETE /categories/{slug}` | Staff | 204; 404 when missing, 409 while it still has Products |

A Customer's token gets 403 on the Staff endpoints, and no token gets 401. Invalid bodies get 400.
Every error is a problem detail.

### Categories and attribute definitions

A Category is `{"slug": "phones", "name": "Phones", "attributes": [...]}`. Each attribute
definition has a `name`, a `type` (`TEXT`, `NUMBER`, `BOOLEAN` or `ENUM`), `values` (an `ENUM`'s
allowed values, and only an `ENUM`'s), `required`, and `variantAxis`. A Variant axis, such as a
phone's `color` or `storage`, tells a Product's Variants apart rather than describing the Product.

Every Product write is checked against its Category's definitions. Its `attributes` must fit the
non-axis ones: each required attribute is present, a `NUMBER` is a decimal such as `"7.5"`, a
`BOOLEAN` is `"true"` or `"false"`, an `ENUM` value is one of its `values`, and there are no
attributes the Category doesn't define or that name a Variant axis. Each Variant's `axisValues`
must cover exactly the Category's Variant axes, each fitting its axis's type, and no two Variants
of a Product may share axis values. Attribute and axis values are always JSON strings. A failure
is a 400 whose `errors` names each offending field:

```json
{"status": 400, "detail": "Product breaks its Category's rules: attributes.screen is required",
 "errors": [{"field": "attributes.screen", "message": "is required"}]}
```

A Variant breaking the axes is named by its position, such as `variants[1].axisValues.storage`, and
a repeated combination as `variants[2].axisValues`. A Product whose `category` names no Category is
a 400 with the field `category`. Changing a Category's definitions never rewrites existing
Products: the new rules apply on each Product's next write.

An invalid Category is a 400 in the same shape, naming its first mistake: `slug` (not lowercase
letters, digits and hyphens, or not the path's on an update), `name`, or a definition's field by
its position, such as `attributes[1].type`, `attributes[1].values` (missing for an `ENUM`, or given
for another type) or `attributes[2].name` (missing, or repeating an earlier one):

```json
{"status": 400, "detail": "Category is invalid: attributes[1].values are required for an ENUM",
 "errors": [{"field": "attributes[1].values", "message": "are required for an ENUM"}]}
```

### Products and Variants

A Product is sold as one or more Variants, such as a phone's colour and storage combinations. Each
Variant has:

- an `id`, the Variant ID, unique across the whole Catalog (a Variant ID another Product has is a
  409) and never changed: an update may add Variants, but one that leaves out or renames any of the
  Product's current Variant IDs is a 409, since Carts, Orders and Stock go on naming them. A Variant
  goes only when its whole Product is deleted, which frees its ID;
- its `axisValues`, such as `{"color": "Obsidian", "storage": "256 GB"}`, returned in the order
  the Category defines its axes;
- its `price`, such as `{"amountMinor": 89900, "currency": "EUR"}`, in one Currency for every
  Variant of a Product (see [Currencies](#currencies));
- optional `images`, which replace the Product's own.

A Cart, an Order and Inventory's Stock all name Variant IDs, never SKUs. A Product has no Price of
its own; listings and the detail carry `priceFrom`, the lowest Variant Price. Its `description`,
free text for Customers, is optional and `null` when it has none; a blank one is stored as none.

```json
{"sku": "PHN-PIXEL-9", "name": "Google Pixel 9", "description": null, "category": "phones",
 "attributes": {"brand": "Google", "screen": "6.3 in"},
 "images": ["/images/products/phn-pixel-9/front.svg"],
 "priceFrom": {"amountMinor": 79900, "currency": "EUR"},
 "variants": [
   {"id": "PHN-PIXEL-9", "axisValues": {"color": "Obsidian", "storage": "128 GB"},
    "price": {"amountMinor": 79900, "currency": "EUR"}, "images": []},
   {"id": "PHN-PIXEL-9-PORCELAIN-128", "axisValues": {"color": "Porcelain", "storage": "128 GB"},
    "price": {"amountMinor": 79900, "currency": "EUR"},
    "images": ["/images/products/phn-pixel-9/porcelain.svg"]}]}
```

Staff send the same shape without `priceFrom`; an update replaces the whole Product, so leaving
`description` out removes it. `GET /variants/{variantId}` answers what a Cart line or a checkout
needs, including the Product's Category, which decides the Campaigns that apply to it:

```json
{"id": "PHN-PIXEL-9-OBSIDIAN-256", "axisValues": {"color": "Obsidian", "storage": "256 GB"},
 "price": {"amountMinor": 89900, "currency": "EUR"}, "images": [],
 "product": {"sku": "PHN-PIXEL-9", "name": "Google Pixel 9", "category": "phones",
             "images": ["/images/products/phn-pixel-9/front.svg"]}}
```

### Currencies

A Price is in one of the Currencies `GET /currencies` lists: every ISO 4217 currency the JDK knows
that has a Minor unit, with how many digits that has.

```json
[{"code": "BHD", "minorDigits": 3}, {"code": "EUR", "minorDigits": 2}, {"code": "JPY", "minorDigits": 0}]
```

`amountMinor` counts in that Minor unit, so clients turn it into a decimal and back by this list,
never by their own currency data. Browsers' `Intl` data disagrees with ISO 4217 for some Currencies,
such as HUF, IDR and IQD (0 digits instead of 2 or 3), and a Price read that way is off a
hundredfold. Codes with no Minor unit, such as `XXX` or `XAU`, are refused.

A Price Catalog can't read is a 400 naming its field:
- a missing one is `variants[1].price`;
- one without an `amountMinor` or a `currency` is also `variants[1].price`;
- one in a currency the list doesn't have is `variants[1].price.currency`.

[`http/catalog.http`](./http/catalog.http) exercises every endpoint against the compose stack.

## Product and Category events

Creating, changing or removing a Product publishes one `catalog.product` event, keyed by its SKU;
the same for a Category publishes one `catalog.category` event, keyed by its slug. Each carries the
Correlation ID of the request as its `X-Correlation-Id` header. Their schemas,
[`catalog.product.json`](../../platform/event-schemas/schemas/catalog.product.json) and
[`catalog.category.json`](../../platform/event-schemas/schemas/catalog.category.json), are the
definition:

```json
{"eventId": "…", "occurredAt": "2026-10-05T10:00:00Z", "sku": "PHN-PIXEL-9", "version": 3,
 "change": "UPDATED",
 "product": {"name": "Google Pixel 9", "category": "phones",
             "attributes": {"brand": "Google", "screen": "6.3 in"},
             "images": ["/images/products/phn-pixel-9/front.svg"],
             "variants": [{"variantId": "PHN-PIXEL-9",
                           "axisValues": {"color": "Obsidian", "storage": "128 GB"},
                           "price": {"amountMinor": 79900, "currency": "EUR"}, "images": []}],
             "removed": false}}
```

```json
{"eventId": "…", "occurredAt": "2026-10-05T10:00:00Z", "slug": "phones", "version": 1,
 "change": "CREATED",
 "category": {"name": "Phones",
              "attributes": [{"name": "brand", "type": "TEXT", "values": [], "required": true,
                              "variantAxis": false}],
              "removed": false}}
```

- `change` is `CREATED`, `UPDATED`, `REMOVED` or `BACKFILLED`.
- A Product event carries the whole Product, every Variant included; `description` is left out when
  it has none.
- A removed Product or Category is published as its last state with `removed: true`, never as a
  tombstone.
- `version` goes up by one with every change to that SKU or slug. It outlives a removal: a SKU
  created again carries on from its last version, so consumers never take the new Product for a
  stale one. Consumers apply an event only when it is newer than what they hold.
- A refused write, such as an invalid Product or the delete of a Category that still has Products,
  publishes nothing.
- On startup, every Product and Category stored before these events (on a Sprint 2 stack, all of
  them) is published once with `change: BACKFILLED`, Categories first. A restart publishes nothing
  more.

Delivery is at least once. The change and its event are written in one Couchbase transaction, so
neither is stored without the other. A relay in the service then sends the event to Kafka, taking
the oldest unsent event of each aggregate, so one aggregate's events go out in version order.

- While Kafka can't take an event, the event stays in the outbox, and that aggregate's later events
  wait behind it. Other aggregates' events carry on.
- After three sends time out in one pass, the relay assumes Kafka is down and tries again on the
  next pass.
- An event Kafka refuses, such as one its schema rejects, is logged at error level on every pass
  and holds back only its own aggregate.

Watch the topics on the compose stack with:

```sh
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic catalog.product --from-beginning --property print.key=true --property print.headers=true
```

## Storage

Each Product is a JSON document in the `catalog` bucket's default collection, keyed by its SKU
and marked `"type": "product"`, with its Variants inside it. A Variant's axis values are stored as
a list of `{"name", "value"}` pairs, so they keep their order. Each Category sits beside them, keyed `category::<slug>` and
marked `"type": "category"`. On startup the service creates the indexes that listings need
(`idx_product_category`, `idx_category_slug`) and Variant lookups need (`idx_product_variant_id`,
an array index on the Variants' IDs) if they are missing. Listings use `REQUEST_PLUS` consistency, so a change
shows up in the next listing.

Writes run in Couchbase distributed transactions, each together with two more documents:

- **Its version**: keyed `version::product::<sku>` or `version::category::<slug>`, marked
  `"type": "version"`. It is never removed, so a SKU or slug keeps counting up. Because every change
  rewrites it, two changes to one aggregate conflict, and Couchbase runs one of them again.
- **Its event**: an outbox document keyed `outbox::<eventId>` and marked `"type": "outbox"`. It holds
  the topic, the key, the version, the Correlation ID and the event's JSON. The relay looks for
  unsent ones every 500 ms (`ecomm.catalog.outbox.relay-every`) through `idx_outbox_pending`, the
  lowest version of each aggregate first. Once Kafka has one, the relay sets its `sentAt`, and the document expires a week later.

A transaction also leaves Couchbase's own `_txn:` records in the bucket. A transaction's writes are
durable at `ecomm.catalog.transaction-durability`. It is `NONE` here, because the local cluster is a
single node with no replica to write to. A production cluster leaves it out and keeps `MAJORITY`.

## Seed data

[`seed/categories.json`](./src/main/resources/seed/categories.json) defines the `phones`,
`laptops` and `audio` Categories, and [`seed/products.json`](./src/main/resources/seed/products.json)
holds 20 Products across them. They are loaded on startup, Categories first, and only into an
empty bucket, so a restart never undoes Staff changes. Each seed Product is checked against its
Category like any other write, so a seed that breaks its definitions stops the service starting.
Seeding publishes each Category and Product as `CREATED`, as Staff writes do.

Each seed Product's first Variant has the SKU as its Variant ID, so Inventory's seed Stock and
existing Carts and Orders stay valid. The Google Pixel 9, the Apple iPhone 16 and the Apple MacBook
Air 13 (M3) come in several Variants along their axes. Not every combination exists, and
Inventory's seed starts one iPhone Variant sold out, so the Storefront's Variant picker has both
cases to show.

A stack seeded before multi-Variant Products has Product documents without `variants`, which the
service can't read. Run `make seed-reset` from the repo root to reload the seed. It also resets
Stock and drops Carts, Orders and Payments.

## Run it

```sh
docker compose up -d --build catalog   # from the repo root; listens on localhost:8081
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/catalog/`.

Or run Couchbase, Keycloak and the event backbone in compose (`docker compose up -d couchbase-init
keycloak kafka-topics schema-registry-init`) and start the service with
`./gradlew :services:catalog:bootRun` on port 8080.
