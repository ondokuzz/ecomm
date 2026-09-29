# Catalog

Products, their Variants, categories and Price. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Data lives in Couchbase ([ADR 0001](./docs/adr/0001-catalog-on-couchbase.md)).

## API

| Endpoint | Who | |
|---|---|---|
| `GET /products?category=` | anyone | Products ordered by name, all of them when `category` is left out |
| `GET /products/{sku}` | anyone | One Product, 404 when missing |
| `GET /categories` | anyone | Categories with their Product counts |
| `POST /products` | Staff | 201, or 409 when the SKU is taken |
| `PUT /products/{sku}` | Staff | Replaces the Product; 404 when missing |
| `DELETE /products/{sku}` | Staff | 204; 404 when missing |

A Customer's token gets 403 on the Staff endpoints, and no token gets 401. Invalid bodies get 400.
Every error is a problem detail.

A Product carries a `variants` list. For now it always holds one Variant, whose `id` is the SKU.
Clients should put that Variant ID in a Cart, not the SKU, so multi-Variant Products won't break
them. Prices are `{"amountMinor": 79900, "currency": "EUR"}`.

[`http/catalog.http`](./http/catalog.http) exercises every endpoint against the compose stack.

## Storage

Each Product is a JSON document in the `catalog` bucket's default collection, keyed by its SKU
and marked `"type": "product"`. On startup the service creates the index that listings need
(`idx_product_category`) if it is missing. Listings use `REQUEST_PLUS` consistency, so a change
shows up in the next listing.

## Seed data

[`seed/products.json`](./src/main/resources/seed/products.json) holds 20 Products across phones,
laptops and audio. They are loaded on startup, and only into an empty bucket, so a restart never
undoes Staff changes. To reload them, flush the bucket or drop the `couchbase-data` volume.

## Run it

```sh
docker compose up -d --build catalog   # from the repo root; listens on localhost:8081
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/catalog/`.

Or run Couchbase and Keycloak in compose (`docker compose up -d couchbase-init keycloak`) and
start the service with `./gradlew :services:catalog:bootRun` on port 8080.
