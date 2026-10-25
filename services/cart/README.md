# Cart

A Customer's own Cart: the Variants they mean to buy and how many of each. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Carts live in the shared Redis, and Cart publishes each
Abandoned Cart to Kafka.

A Cart holds only Variant IDs and quantities. It has no price and doesn't check that a Variant
exists or is in stock; checkout does that against Catalog and Inventory.

## API

Every endpoint needs a token with the `CUSTOMER` role, and acts on the Cart of the Customer it
names (its `sub`). No one can reach another Customer's Cart. Staff have no Cart: a token without
`CUSTOMER` gets 403.

| Endpoint | |
|---|---|
| `GET /cart` | `{"items": [{"variantId", "quantity"}]}` in Variant ID order; empty if there is no Cart |
| `PUT /cart/items/{variantId}` | Body `{"quantity": 2}`: sets that Variant's quantity, adding it if need be; 200 with the Cart |
| `DELETE /cart/items/{variantId}` | Takes the Variant out, if it is there; 200 with the Cart |
| `DELETE /cart` | Empties the Cart; 204 |

The quantity must be a positive JSON integer; anything else (`0`, `-1`, `1.5`, `"2"`, `null`, a
missing field, a number past 2^31−1) is a 400. Every error is a problem detail.

One endpoint is internal. The checkout Saga clears a Customer's Cart once their Order is paid, with
Orchestration's own token (`ORCHESTRATION`,
[ADR 0002](../identity-access/docs/adr/0002-service-identity-by-client-credentials.md)), naming
the Customer in the body. Any other token, a Customer's or Checkout's included, gets 403, and the
API gateway doesn't route it. Clearing an empty Cart changes nothing, so it takes no
`Idempotency-Key`.

| Endpoint | |
|---|---|
| `POST /carts/clear` | Body `{"customerId": "…"}`: empties that Customer's Cart; 204. A missing or blank `customerId` is a 400 |

[`http/cart.http`](./http/cart.http) exercises every endpoint against the compose stack.

## Abandoned Carts

A Cart that holds Variants and hasn't changed for `ecomm.cart.abandoned-after` (24 hours) is an
[Abandoned Cart](../../GLOSSARY.md), recorded once per abandonment. Every
`ecomm.cart.abandonment-sweep` (5 minutes) a sweep finds the Carts idle past it and not yet marked.
For each, it publishes a `cart.cart` event with `change: ABANDONED`, the Customer, the lines and
`lastChangedAt` ([schema](../../platform/event-schemas/schemas/cart.cart.json)), then marks the Cart.

- A change after that makes the Cart a candidate again; removing a Variant the Cart doesn't hold
  isn't one. A Cart emptied, by the Customer or by the checkout Saga once its Order is paid, is
  never abandoned. The one exception is a Cart emptied in the moment between the sweep reading it
  and publishing it: it is published, though not marked or counted, and a consumer can tell it by
  reading the Cart.
- One instance sweeps at a time, holding a Redis lock, `carts:abandonment-sweep`, which lapses after
  10 minutes should an instance die holding it. A sweep starts no batch of 100 after 2 minutes,
  leaving the rest to the next.
- There is no outbox ([ADR 0002](../../docs/adr/0002-ledgers-and-outboxes-not-event-sourcing.md)'s
  2026-10-25 note). The event goes straight to Kafka with the idempotent producer, and the Cart is
  marked only once Kafka acknowledges it. A sweep that fails or stops in between leaves the Cart
  unmarked, and the next one publishes the same event, with the same `eventId`, derived from the
  Customer and `lastChangedAt`. A consumer dedupes it.
- `cart_abandoned_total` counts the Carts marked abandoned, from 0, and Grafana's Services dashboard
  plots it per hour.

Nothing consumes `cart.cart` yet; Sprint 8's abandoned-cart recovery will. To see one by hand,
shorten the threshold and the sweep with a Compose override, such as
`ECOMM_CART_ABANDONED_AFTER=2m` and `ECOMM_CART_ABANDONMENT_SWEEP=30s`.

## Storage

One Redis hash per Customer, `cart:<Customer ID>`, mapping Variant ID to quantity. A Cart lasts 7
days after its last change: setting a quantity or removing a Variant resets the hash's TTL, in the
same Lua script as the change so a Cart is never left without one. Reading a Cart doesn't extend it.

The sorted set `carts:last-change` holds each Cart not yet marked abandoned, its Customer ID scored
by the Cart's last change in epoch milliseconds, written in the same script as every change.
Marking a Cart abandoned takes it out of the set and sets the hash's `/abandoned` field to that last
change; the next change clears the field and puts the Cart back. Emptying or clearing a Cart takes
it out too. On start, Cart adds each Cart in neither, such as one from before abandonment was
detected, as changed then.

## Run it

```sh
docker compose up -d --build cart   # from the repo root; listens on localhost:8083
```

That host port bypasses the [API gateway](../../platform/api-gateway/README.md), for development
only. Browsers reach the service through the gateway, at `/api/cart/`.

Or run Redis, Keycloak and the event backbone in compose
(`docker compose up -d redis keycloak kafka-topics schema-registry-init`) and start the service
with `./gradlew :services:cart:bootRun` on port 8080. Set `REDIS_PORT`, `KAFKA_PORT` and
`APICURIO_PORT` for both when something else holds 6379, 9092 or 8088.
