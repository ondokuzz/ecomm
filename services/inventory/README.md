# Inventory

Stock levels per Variant. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Data lives in the `inventory` database on the shared Postgres,
with the schema under Flyway ([`db/migration`](./src/main/resources/db/migration)).

For Sprint 1 stock is a plain counter: checkout decrements it directly. Reservations come in
Sprint 2, and an event-sourced rebuild in Sprint 4 (see the [roadmap](../../docs/roadmap.md)).

## API

| Endpoint | Who | |
|---|---|---|
| `GET /stock/{variantId}` | anyone | `{"variantId", "quantity"}`; 404 for an unknown Variant |
| `POST /stock/decrement` | Checkout | Takes a batch off stock; 200 with each Variant's new stock |

Decrementing needs Checkout's own token, with the `CHECKOUT` role
([ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)). A Customer's
or Staff token gets 403, and no token gets 401.

A decrement looks like `{"items": [{"variantId": "PHN-PIXEL-9", "quantity": 2}, ...]}`. It applies
in one transaction, whole or not at all:

- If any Variant would go below zero, it is a 409 whose `insufficientStock` lists those Variants.
- If any Variant is unknown, it is a 404 whose `unknownVariants` lists them.
- An empty batch, a missing `variantId`, or a quantity that isn't positive is a 400.

A Variant listed twice is decremented by the total. Every error is a problem detail.

[`http/inventory.http`](./http/inventory.http) exercises every endpoint against the compose stack.

## Storage

One `stock` row per Variant, with a check constraint keeping `quantity` non-negative. A decrement
locks its Variants' rows (`SELECT ... FOR UPDATE`, in Variant ID order so overlapping batches can't
deadlock), checks the whole batch, then writes it.

## Seed data

`V2__seed_stock.sql` gives stock to the first Variant of each of Catalog's
[seed Products](../catalog/src/main/resources/seed/products.json), whose Variant ID is its SKU, and
`V3__seed_variant_stock.sql` to the extra Variants of its multi-Variant ones, one of them sold out.
They are migrations, so each runs once and a restart never undoes a decrement; `ReadStockApiTest`
fails if they drift from Catalog's seed. To reset stock, drop and recreate the `inventory` database.

## Run it

```sh
docker compose up -d --build inventory   # from the repo root; listens on localhost:8082
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/inventory/`.

Or run Postgres and Keycloak in compose (`docker compose up -d keycloak`) and start the service
with `./gradlew :services:inventory:bootRun` on port 8080. Set `POSTGRES_PORT` for both when
something else holds 5432.
