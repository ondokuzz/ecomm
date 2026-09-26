# Cart

A Customer's own Cart: the Variants they mean to buy and how many of each. Built from
[`platform/service-template`](../../platform/service-template/README.md), so its layout, security
and testing conventions apply here. Carts live in the shared Redis.

A Cart holds only Variant IDs and quantities. It has no price and doesn't check that a Variant
exists or is in stock; checkout does that against Catalog and Inventory.

## API

Every endpoint needs a token, and acts on the Cart of the Customer it names (its `sub`). No one can
reach another Customer's Cart.

| Endpoint | |
|---|---|
| `GET /cart` | `{"items": [{"variantId", "quantity"}]}` in Variant ID order; empty if there is no Cart |
| `PUT /cart/items/{variantId}` | Body `{"quantity": 2}`: sets that Variant's quantity, adding it if need be; 200 with the Cart |
| `DELETE /cart/items/{variantId}` | Takes the Variant out, if it is there; 200 with the Cart |
| `DELETE /cart` | Empties the Cart; 204 |

The quantity must be a positive JSON integer; anything else (`0`, `-1`, `1.5`, `"2"`, `null`, a
missing field, a number past 2^31−1) is a 400. Every error is a problem detail.

[`http/cart.http`](./http/cart.http) exercises every endpoint against the compose stack.

## Storage

One Redis hash per Customer, `cart:<Customer ID>`, mapping Variant ID to quantity. A Cart lasts 7
days after its last change: setting a quantity or removing a Variant resets the hash's TTL, in the
same Lua script as the change so a Cart is never left without one. Reading a Cart doesn't extend it.

## Run it

```sh
docker compose up -d --build cart   # from the repo root; listens on localhost:8083
```

Or run Redis and Keycloak in compose (`docker compose up -d redis keycloak`) and start the service
with `./gradlew :services:cart:bootRun` on port 8080. Set `REDIS_PORT` for both when something else
holds 6379.
