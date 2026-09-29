# Catalog

Products, their Variants, Categories and Price. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Data lives in Couchbase ([ADR 0001](./docs/adr/0001-catalog-on-couchbase.md)).

## API

| Endpoint | Who | |
|---|---|---|
| `GET /products?category=` | anyone | Products ordered by name, all of them when `category` is left out |
| `GET /products/{sku}` | anyone | One Product, 404 when missing |
| `GET /categories` | anyone | Categories ordered by slug, with name, Product count and attribute definitions |
| `GET /categories/{slug}` | anyone | One Category, 404 when missing |
| `POST /products` | Staff | 201, or 409 when the SKU is taken |
| `PUT /products/{sku}` | Staff | Replaces the Product; 404 when missing |
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

Every Product write is checked against its Category's non-axis definitions: each required
attribute is present, a `NUMBER` is a decimal such as `"7.5"`, a `BOOLEAN` is `"true"` or
`"false"`, an `ENUM` value is one of its `values`, and there are no attributes the Category
doesn't define. Attribute values are always JSON strings. A failure is a 400 whose `errors` names
each offending field:

```json
{"status": 400, "detail": "Product breaks its Category's rules: attributes.screen is required",
 "errors": [{"field": "attributes.screen", "message": "is required"}]}
```

A Product whose `category` names no Category is a 400 with the field `category`. An attribute named
after a Variant axis is never required, since axis values move onto Variants with multi-Variant
Products, but one a Product still carries must fit the axis's type. Changing a Category's definitions never rewrites existing Products: the
new rules apply on each Product's next write.

A Product carries a `variants` list. For now it always holds one Variant, whose `id` is the SKU.
Clients should put that Variant ID in a Cart, not the SKU, so multi-Variant Products won't break
them. Prices are `{"amountMinor": 79900, "currency": "EUR"}`.

[`http/catalog.http`](./http/catalog.http) exercises every endpoint against the compose stack.

## Storage

Each Product is a JSON document in the `catalog` bucket's default collection, keyed by its SKU
and marked `"type": "product"`. Each Category sits beside them, keyed `category::<slug>` and
marked `"type": "category"`. On startup the service creates the indexes that listings need
(`idx_product_category`, `idx_category_slug`) if they are missing. Listings use `REQUEST_PLUS` consistency, so a change
shows up in the next listing.

## Seed data

[`seed/categories.json`](./src/main/resources/seed/categories.json) defines the `phones`,
`laptops` and `audio` Categories, and [`seed/products.json`](./src/main/resources/seed/products.json)
holds 20 Products across them. They are loaded on startup, Categories first, and only into an
empty bucket, so a restart never undoes Staff changes. Each seed Product is checked against its
Category like any other write, so a seed that breaks its definitions stops the service starting.

A stack seeded before Categories existed has Products but no Categories, so it lists no
Categories and refuses every Product write. Run `make seed-reset` from the repo root to reload the
seed. It also resets Stock and drops Carts, Orders and Payments.

## Run it

```sh
docker compose up -d --build catalog   # from the repo root; listens on localhost:8081
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/catalog/`.

Or run Couchbase and Keycloak in compose (`docker compose up -d couchbase-init keycloak`) and
start the service with `./gradlew :services:catalog:bootRun` on port 8080.
